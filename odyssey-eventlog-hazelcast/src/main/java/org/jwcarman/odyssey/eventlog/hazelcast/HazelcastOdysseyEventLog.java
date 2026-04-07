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
package org.jwcarman.odyssey.eventlog.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.ringbuffer.Ringbuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.AbstractOdysseyEventLog;
import tools.jackson.databind.ObjectMapper;

public class HazelcastOdysseyEventLog extends AbstractOdysseyEventLog {

  private final HazelcastInstance hazelcast;
  private final ObjectMapper objectMapper;
  private final int batchSize;

  public HazelcastOdysseyEventLog(
      HazelcastInstance hazelcast,
      ObjectMapper objectMapper,
      int batchSize,
      String ephemeralPrefix,
      String channelPrefix,
      String broadcastPrefix) {
    super(ephemeralPrefix, channelPrefix, broadcastPrefix);
    this.hazelcast = hazelcast;
    this.objectMapper = objectMapper;
    this.batchSize = batchSize;
  }

  @Override
  public String append(String streamKey, OdysseyEvent event) {
    Ringbuffer<String> ringbuffer = hazelcast.getRingbuffer(streamKey);
    String json = serialize(event);
    long sequence = ringbuffer.add(json);
    return String.valueOf(sequence);
  }

  @Override
  public Stream<OdysseyEvent> readAfter(String streamKey, String lastId) {
    Ringbuffer<String> ringbuffer = hazelcast.getRingbuffer(streamKey);
    long startSequence = Long.parseLong(lastId) + 1;
    long tailSequence = ringbuffer.tailSequence();

    if (startSequence > tailSequence || tailSequence == -1) {
      return Stream.empty();
    }

    long headSequence = ringbuffer.headSequence();
    long readFrom = Math.max(startSequence, headSequence);
    int count = (int) Math.min(tailSequence - readFrom + 1, batchSize);

    if (count <= 0) {
      return Stream.empty();
    }

    try {
      var resultSet =
          ringbuffer.readManyAsync(readFrom, 0, count, null).toCompletableFuture().join();
      List<OdysseyEvent> events = new ArrayList<>();
      for (int i = 0; i < resultSet.readCount(); i++) {
        String json = resultSet.get(i);
        OdysseyEvent event = deserialize(json, streamKey, String.valueOf(readFrom + i));
        events.add(event);
      }
      return events.stream();
    } catch (Exception e) {
      return Stream.empty();
    }
  }

  @Override
  public Stream<OdysseyEvent> readLast(String streamKey, int count) {
    Ringbuffer<String> ringbuffer = hazelcast.getRingbuffer(streamKey);
    long tailSequence = ringbuffer.tailSequence();

    if (tailSequence == -1) {
      return Stream.empty();
    }

    long headSequence = ringbuffer.headSequence();
    long startSequence = Math.max(headSequence, tailSequence - count + 1);
    int readCount = (int) (tailSequence - startSequence + 1);

    if (readCount <= 0) {
      return Stream.empty();
    }

    try {
      var resultSet =
          ringbuffer.readManyAsync(startSequence, 0, readCount, null).toCompletableFuture().join();
      List<OdysseyEvent> events = new ArrayList<>();
      for (int i = 0; i < resultSet.readCount(); i++) {
        String json = resultSet.get(i);
        OdysseyEvent event = deserialize(json, streamKey, String.valueOf(startSequence + i));
        events.add(event);
      }
      return events.stream();
    } catch (Exception e) {
      return Stream.empty();
    }
  }

  @Override
  public void delete(String streamKey) {
    Ringbuffer<String> ringbuffer = hazelcast.getRingbuffer(streamKey);
    ringbuffer.destroy();
  }

  private String serialize(OdysseyEvent event) {
    try {
      return objectMapper.writeValueAsString(
          new StoredEvent(
              event.eventType(), event.payload(), event.timestamp().toString(), event.metadata()));
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalStateException("Failed to serialize OdysseyEvent", e);
    }
  }

  private OdysseyEvent deserialize(String json, String streamKey, String id) {
    try {
      StoredEvent stored = objectMapper.readValue(json, StoredEvent.class);
      return OdysseyEvent.builder()
          .id(id)
          .streamKey(streamKey)
          .eventType(stored.eventType())
          .payload(stored.payload())
          .timestamp(Instant.parse(stored.timestamp()))
          .metadata(stored.metadata() != null ? stored.metadata() : Map.of())
          .build();
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalStateException("Failed to deserialize OdysseyEvent", e);
    }
  }

  record StoredEvent(
      String eventType, String payload, String timestamp, Map<String, String> metadata) {}
}
