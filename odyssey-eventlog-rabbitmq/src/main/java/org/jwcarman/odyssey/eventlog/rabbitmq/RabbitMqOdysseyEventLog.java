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
package org.jwcarman.odyssey.eventlog.rabbitmq;

import com.rabbitmq.stream.ByteCapacity;
import com.rabbitmq.stream.Consumer;
import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.OffsetSpecification;
import com.rabbitmq.stream.Producer;
import com.rabbitmq.stream.StreamException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.AbstractOdysseyEventLog;

public class RabbitMqOdysseyEventLog extends AbstractOdysseyEventLog implements AutoCloseable {

  private static final long CONSUME_TIMEOUT_MS = 200;
  private static final long PUBLISH_TIMEOUT_SECONDS = 5;

  private final Environment environment;
  private final Duration maxAge;
  private final long maxLengthBytes;
  private final ConcurrentHashMap<String, Producer> producers = new ConcurrentHashMap<>();

  public RabbitMqOdysseyEventLog(
      Environment environment,
      Duration maxAge,
      long maxLengthBytes,
      String ephemeralPrefix,
      String channelPrefix,
      String broadcastPrefix) {
    super(ephemeralPrefix, channelPrefix, broadcastPrefix);
    this.environment = environment;
    this.maxAge = maxAge;
    this.maxLengthBytes = maxLengthBytes;
  }

  @Override
  public String append(String streamKey, OdysseyEvent event) {
    ensureStreamExists(streamKey);
    String eventId = generateEventId();
    Producer producer = getOrCreateProducer(streamKey);

    Message message = buildMessage(producer, eventId, streamKey, event);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Exception> error = new AtomicReference<>();
    producer.send(
        message,
        status -> {
          if (!status.isConfirmed()) {
            error.set(new StreamException("Message not confirmed: " + status.getCode()));
          }
          latch.countDown();
        });

    try {
      if (!latch.await(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new StreamException("Publish confirmation timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StreamException("Interrupted while waiting for publish confirmation");
    }

    if (error.get() != null) {
      throw new StreamException("Failed to publish message", error.get());
    }

    return eventId;
  }

  @Override
  public Stream<OdysseyEvent> readAfter(String streamKey, String lastId) {
    return consumeAll(streamKey).stream().filter(event -> event.id().compareTo(lastId) > 0);
  }

  @Override
  public Stream<OdysseyEvent> readLast(String streamKey, int count) {
    List<OdysseyEvent> all = consumeAll(streamKey);
    int start = Math.max(0, all.size() - count);
    return all.subList(start, all.size()).stream();
  }

  @Override
  public void delete(String streamKey) {
    Producer producer = producers.remove(streamKey);
    if (producer != null) {
      producer.close();
    }
    try {
      environment.deleteStream(streamKey);
    } catch (StreamException e) {
      // Stream doesn't exist — safe to ignore
    }
  }

  @Override
  public void close() {
    producers.values().forEach(Producer::close);
    producers.clear();
  }

  private void ensureStreamExists(String streamKey) {
    try {
      environment.streamCreator().stream(streamKey)
          .maxAge(maxAge)
          .maxLengthBytes(ByteCapacity.B(maxLengthBytes))
          .create();
    } catch (StreamException e) {
      // Stream already exists — safe to ignore
    }
  }

  private Producer getOrCreateProducer(String streamKey) {
    return producers.computeIfAbsent(
        streamKey, key -> environment.producerBuilder().stream(key).build());
  }

  private Message buildMessage(
      Producer producer, String eventId, String streamKey, OdysseyEvent event) {
    var appProps =
        producer
            .messageBuilder()
            .applicationProperties()
            .entry("eventId", eventId)
            .entry("streamKey", streamKey)
            .entry("eventType", event.eventType())
            .entry("timestamp", event.timestamp().toString());

    if (event.metadata() != null && !event.metadata().isEmpty()) {
      for (var entry : event.metadata().entrySet()) {
        appProps.entry("meta." + entry.getKey(), entry.getValue());
      }
    }

    byte[] body =
        event.payload() != null ? event.payload().getBytes(StandardCharsets.UTF_8) : new byte[0];

    return appProps.messageBuilder().addData(body).build();
  }

  private List<OdysseyEvent> consumeAll(String streamKey) {
    if (!streamExists(streamKey)) {
      return List.of();
    }

    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    Consumer consumer;
    try {
      consumer =
          environment.consumerBuilder().stream(streamKey)
              .offset(OffsetSpecification.first())
              .messageHandler((ctx, msg) -> queue.add(deserializeMessage(msg)))
              .build();
    } catch (StreamException e) {
      return List.of();
    }

    List<OdysseyEvent> events = new ArrayList<>();
    try {
      OdysseyEvent event;
      while ((event = queue.poll(CONSUME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) != null) {
        events.add(event);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      consumer.close();
    }

    return Collections.unmodifiableList(events);
  }

  private boolean streamExists(String streamKey) {
    try {
      environment.queryStreamStats(streamKey);
      return true;
    } catch (StreamException e) {
      return false;
    }
  }

  private OdysseyEvent deserializeMessage(Message message) {
    Map<String, Object> props = message.getApplicationProperties();

    Map<String, String> metadata = new HashMap<>();
    if (props != null) {
      props.forEach(
          (k, v) -> {
            if (k.startsWith("meta.")) {
              metadata.put(k.substring(5), String.valueOf(v));
            }
          });
    }

    String payload =
        message.getBodyAsBinary() != null
            ? new String(message.getBodyAsBinary(), StandardCharsets.UTF_8)
            : null;

    return OdysseyEvent.builder()
        .id(props != null ? (String) props.get("eventId") : null)
        .streamKey(props != null ? (String) props.get("streamKey") : null)
        .eventType(props != null ? (String) props.get("eventType") : null)
        .payload(payload)
        .timestamp(
            props != null && props.get("timestamp") != null
                ? Instant.parse((String) props.get("timestamp"))
                : null)
        .metadata(metadata)
        .build();
  }
}
