/*
 * Copyright © 2026 James Carman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwcarman.odyssey.eventlog.nats;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.PurgeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.api.StreamInfoOptions;
import io.nats.client.api.Subject;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.AbstractOdysseyEventLog;

public class NatsOdysseyEventLog extends AbstractOdysseyEventLog {

  private static final String SUBJECT_PREFIX = "odyssey.events.";
  private static final Duration FETCH_TIMEOUT = Duration.ofMillis(500);

  private final JetStream jetStream;
  private final JetStreamManagement jsm;
  private final String streamName;

  public NatsOdysseyEventLog(
      Connection connection,
      String streamName,
      Duration maxAge,
      long maxMessages,
      String ephemeralPrefix,
      String channelPrefix,
      String broadcastPrefix) {
    super(ephemeralPrefix, channelPrefix, broadcastPrefix);
    this.streamName = streamName;
    try {
      this.jetStream = connection.jetStream();
      this.jsm = connection.jetStreamManagement();
      ensureStreamExists(maxAge, maxMessages);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to initialize NATS JetStream", e);
    }
  }

  @Override
  public String append(String streamKey, OdysseyEvent event) {
    try {
      String subject = toSubject(streamKey);

      byte[] payload =
          event.payload() != null ? event.payload().getBytes(StandardCharsets.UTF_8) : new byte[0];

      Headers headers = new Headers();
      headers.add("streamKey", streamKey);
      headers.add("eventType", event.eventType());
      headers.add("timestamp", event.timestamp().toString());
      if (event.metadata() != null && !event.metadata().isEmpty()) {
        for (var entry : event.metadata().entrySet()) {
          headers.add("meta." + entry.getKey(), entry.getValue());
        }
      }

      NatsMessage message =
          NatsMessage.builder().subject(subject).headers(headers).data(payload).build();

      var ack = jetStream.publish(message);
      return String.valueOf(ack.getSeqno());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to publish to NATS JetStream", e);
    } catch (JetStreamApiException e) {
      throw new IllegalStateException("Failed to publish to NATS JetStream", e);
    }
  }

  @Override
  public Stream<OdysseyEvent> readAfter(String streamKey, String lastId) {
    long startSeq = Long.parseLong(lastId) + 1;
    String subject = toSubject(streamKey);

    try {
      StreamInfo info = jsm.getStreamInfo(streamName);
      long lastSeq = info.getStreamState().getLastSequence();
      if (startSeq > lastSeq) {
        return Stream.empty();
      }
      return fetchMessages(subject, DeliverPolicy.ByStartSequence, startSeq, streamKey);
    } catch (IOException | JetStreamApiException _) {
      return Stream.empty();
    }
  }

  @Override
  public Stream<OdysseyEvent> readLast(String streamKey, int count) {
    String subject = toSubject(streamKey);

    try {
      long subjectMessageCount = getSubjectMessageCount(subject);
      if (subjectMessageCount == 0) {
        return Stream.empty();
      }

      List<OdysseyEvent> allEvents = fetchAllForSubject(subject, streamKey);
      int start = Math.max(0, allEvents.size() - count);
      return allEvents.subList(start, allEvents.size()).stream();
    } catch (IOException | JetStreamApiException _) {
      return Stream.empty();
    }
  }

  @Override
  public void delete(String streamKey) {
    String subject = toSubject(streamKey);
    try {
      jsm.purgeStream(streamName, PurgeOptions.subject(subject));
    } catch (IOException | JetStreamApiException _) {
      // Stream or subject doesn't exist — safe to ignore
    }
  }

  private void ensureStreamExists(Duration maxAge, long maxMessages) {
    StreamConfiguration config =
        StreamConfiguration.builder()
            .name(streamName)
            .subjects(SUBJECT_PREFIX + ">")
            .retentionPolicy(RetentionPolicy.Limits)
            .storageType(StorageType.File)
            .maxAge(maxAge)
            .maxMessages(maxMessages)
            .build();
    try {
      jsm.addStream(config);
    } catch (JetStreamApiException e) {
      if (e.getApiErrorCode() == 10058) {
        try {
          jsm.updateStream(config);
        } catch (IOException ex) {
          throw new UncheckedIOException("Failed to update NATS JetStream stream", ex);
        } catch (JetStreamApiException ex) {
          throw new IllegalStateException("Failed to update NATS JetStream stream", ex);
        }
      } else {
        throw new IllegalStateException("Failed to create NATS JetStream stream", e);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create NATS JetStream stream", e);
    }
  }

  private String toSubject(String streamKey) {
    return SUBJECT_PREFIX + streamKey.replace(':', '.');
  }

  private Stream<OdysseyEvent> fetchMessages(
      String subject, DeliverPolicy deliverPolicy, long startSeq, String streamKey) {
    try {
      ConsumerConfiguration.Builder ccBuilder =
          ConsumerConfiguration.builder().filterSubject(subject).deliverPolicy(deliverPolicy);
      if (deliverPolicy == DeliverPolicy.ByStartSequence) {
        ccBuilder.startSequence(startSeq);
      }

      PullSubscribeOptions pullOptions =
          PullSubscribeOptions.builder().stream(streamName)
              .configuration(ccBuilder.build())
              .build();

      JetStreamSubscription sub = jetStream.subscribe(subject, pullOptions);

      List<OdysseyEvent> events = new ArrayList<>();
      try {
        List<Message> batch = sub.fetch(1000, FETCH_TIMEOUT);
        for (Message msg : batch) {
          events.add(deserializeMessage(msg, streamKey));
        }
      } finally {
        sub.unsubscribe();
      }
      return events.stream();
    } catch (IOException | JetStreamApiException _) {
      return Stream.empty();
    }
  }

  private List<OdysseyEvent> fetchAllForSubject(String subject, String streamKey)
      throws IOException, JetStreamApiException {
    PullSubscribeOptions pullOptions =
        PullSubscribeOptions.builder().stream(streamName)
            .configuration(
                ConsumerConfiguration.builder()
                    .filterSubject(subject)
                    .deliverPolicy(DeliverPolicy.All)
                    .build())
            .build();

    JetStreamSubscription sub = jetStream.subscribe(subject, pullOptions);

    List<OdysseyEvent> events = new ArrayList<>();
    try {
      List<Message> batch = sub.fetch(1000, FETCH_TIMEOUT);
      for (Message msg : batch) {
        events.add(deserializeMessage(msg, streamKey));
      }
    } finally {
      sub.unsubscribe();
    }
    return events;
  }

  private long getSubjectMessageCount(String subject) throws IOException, JetStreamApiException {
    StreamInfo info = jsm.getStreamInfo(streamName, StreamInfoOptions.filterSubjects(subject));
    if (info.getStreamState().getSubjectCount() == 0) {
      return 0;
    }
    List<Subject> subjects = info.getStreamState().getSubjects();
    if (subjects == null || subjects.isEmpty()) {
      return 0;
    }
    return subjects.getFirst().getCount();
  }

  private OdysseyEvent deserializeMessage(Message message, String streamKey) {
    Headers headers = message.getHeaders();
    Map<String, String> metadata = extractMetadata(headers);

    String payload =
        message.getData() != null && message.getData().length > 0
            ? new String(message.getData(), StandardCharsets.UTF_8)
            : null;

    String eventType = headers != null ? getSingleHeader(headers, "eventType") : null;
    String timestamp = headers != null ? getSingleHeader(headers, "timestamp") : null;

    return OdysseyEvent.builder()
        .id(String.valueOf(message.metaData().streamSequence()))
        .streamKey(streamKey)
        .eventType(eventType)
        .payload(payload)
        .timestamp(timestamp != null ? Instant.parse(timestamp) : null)
        .metadata(metadata)
        .build();
  }

  private Map<String, String> extractMetadata(Headers headers) {
    Map<String, String> metadata = new HashMap<>();
    if (headers == null) {
      return metadata;
    }
    for (String key : headers.keySet()) {
      if (key.startsWith("meta.")) {
        List<String> values = headers.get(key);
        if (values != null && !values.isEmpty()) {
          metadata.put(key.substring(5), values.getFirst());
        }
      }
    }
    return metadata;
  }

  private String getSingleHeader(Headers headers, String key) {
    List<String> values = headers.get(key);
    return values != null && !values.isEmpty() ? values.getFirst() : null;
  }
}
