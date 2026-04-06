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
package org.jwcarman.odyssey.eventlog.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CassandraOdysseyEventLogIT {

  @Container static CassandraContainer cassandra = new CassandraContainer("cassandra:4.1");

  private CassandraOdysseyEventLog eventLog;
  private CqlSession session;

  @BeforeEach
  void setUp() {
    session =
        CqlSession.builder()
            .addContactPoint(
                new InetSocketAddress(cassandra.getHost(), cassandra.getMappedPort(9042)))
            .withLocalDatacenter("datacenter1")
            .build();

    session.execute(
        "CREATE KEYSPACE IF NOT EXISTS odyssey_test"
            + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
    session.execute("USE odyssey_test");
    session.execute(
        "CREATE TABLE IF NOT EXISTS odyssey_events ("
            + " stream_key TEXT,"
            + " event_id TIMEUUID,"
            + " event_type TEXT,"
            + " payload TEXT,"
            + " timestamp TIMESTAMP,"
            + " metadata MAP<TEXT, TEXT>,"
            + " PRIMARY KEY (stream_key, event_id)"
            + ") WITH CLUSTERING ORDER BY (event_id ASC)"
            + " AND default_time_to_live = 86400");
    session.execute("TRUNCATE odyssey_events");

    eventLog = new CassandraOdysseyEventLog(session, 0, "ephemeral:", "channel:", "broadcast:");
  }

  @Test
  void appendReturnsValidTimeuuid() {
    OdysseyEvent event = buildEvent("test-stream", "msg", "hello");
    String id = eventLog.append("test-stream", event);

    UUID uuid = UUID.fromString(id);
    assertThat(uuid).isNotNull();
    assertThat(uuid.version()).isEqualTo(1);
  }

  @Test
  void appendReturnsMonotonicallyIncreasingIds() {
    OdysseyEvent event = buildEvent("test-stream", "msg", "hello");
    String id1 = eventLog.append("test-stream", event);
    String id2 = eventLog.append("test-stream", event);

    UUID uuid1 = UUID.fromString(id1);
    UUID uuid2 = UUID.fromString(id2);
    assertThat(uuid2.timestamp()).isGreaterThanOrEqualTo(uuid1.timestamp());
  }

  @Test
  void readAfterReturnsEventsInOrder() {
    String streamKey = "channel:orders";
    String id1 = eventLog.append(streamKey, buildEvent(streamKey, "order.created", "payload1"));
    String id2 = eventLog.append(streamKey, buildEvent(streamKey, "order.updated", "payload2"));
    String id3 = eventLog.append(streamKey, buildEvent(streamKey, "order.shipped", "payload3"));

    List<OdysseyEvent> events = eventLog.readAfter(streamKey, id1).toList();

    assertThat(events).hasSize(2);
    assertThat(events.get(0).id()).isEqualTo(id2);
    assertThat(events.get(0).eventType()).isEqualTo("order.updated");
    assertThat(events.get(1).id()).isEqualTo(id3);
    assertThat(events.get(1).eventType()).isEqualTo("order.shipped");
  }

  @Test
  void readAfterReturnsEmptyForUnknownStream() {
    UUID dummyUuid = com.datastax.oss.driver.api.core.uuid.Uuids.timeBased();
    List<OdysseyEvent> events = eventLog.readAfter("nonexistent", dummyUuid.toString()).toList();
    assertThat(events).isEmpty();
  }

  @Test
  void readLastReturnsLastNInChronologicalOrder() {
    String streamKey = "broadcast:announcements";
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "first"));
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "second"));
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "third"));
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "fourth"));

    List<OdysseyEvent> events = eventLog.readLast(streamKey, 2).toList();

    assertThat(events).hasSize(2);
    assertThat(events.get(0).payload()).isEqualTo("third");
    assertThat(events.get(1).payload()).isEqualTo("fourth");
  }

  @Test
  void readLastReturnsEmptyForUnknownStream() {
    List<OdysseyEvent> events = eventLog.readLast("nonexistent", 5).toList();
    assertThat(events).isEmpty();
  }

  @Test
  void deleteRemovesAllEventsForStream() {
    String streamKey = "ephemeral:abc";
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "hello"));
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "world"));

    eventLog.delete(streamKey);

    List<OdysseyEvent> events = eventLog.readLast(streamKey, 100).toList();
    assertThat(events).isEmpty();
  }

  @Test
  void deleteDoesNotAffectOtherStreams() {
    String stream1 = "channel:a";
    String stream2 = "channel:b";
    eventLog.append(stream1, buildEvent(stream1, "msg", "a-event"));
    eventLog.append(stream2, buildEvent(stream2, "msg", "b-event"));

    eventLog.delete(stream1);

    assertThat(eventLog.readLast(stream1, 100).toList()).isEmpty();
    assertThat(eventLog.readLast(stream2, 100).toList()).hasSize(1);
  }

  @Test
  void metadataIsPreserved() {
    String streamKey = "channel:meta";
    Map<String, String> metadata = Map.of("userId", "42", "source", "api");
    OdysseyEvent event =
        OdysseyEvent.builder()
            .streamKey(streamKey)
            .eventType("test")
            .payload("data")
            .timestamp(Instant.now())
            .metadata(metadata)
            .build();

    eventLog.append(streamKey, event);

    List<OdysseyEvent> events = eventLog.readLast(streamKey, 1).toList();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().metadata()).containsEntry("userId", "42");
    assertThat(events.getFirst().metadata()).containsEntry("source", "api");
  }

  @Test
  void timestampIsPreserved() {
    String streamKey = "channel:time";
    Instant now = Instant.now();
    OdysseyEvent event =
        OdysseyEvent.builder()
            .streamKey(streamKey)
            .eventType("test")
            .payload("data")
            .timestamp(now)
            .build();

    eventLog.append(streamKey, event);

    List<OdysseyEvent> events = eventLog.readLast(streamKey, 1).toList();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().timestamp().toEpochMilli()).isEqualTo(now.toEpochMilli());
  }

  @Test
  void appendWithTtlDoesNotError() {
    CassandraOdysseyEventLog ttlLog =
        new CassandraOdysseyEventLog(session, 3600, "ephemeral:", "channel:", "broadcast:");

    String streamKey = "ephemeral:ttl-test";
    String id = ttlLog.append(streamKey, buildEvent(streamKey, "msg", "ttl-event"));
    assertThat(id).isNotEmpty();

    List<OdysseyEvent> events = ttlLog.readLast(streamKey, 1).toList();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().payload()).isEqualTo("ttl-event");
  }

  @Test
  void ephemeralKeyGeneratesUniqueKeys() {
    String key1 = eventLog.ephemeralKey();
    String key2 = eventLog.ephemeralKey();

    assertThat(key1).startsWith("ephemeral:");
    assertThat(key2).startsWith("ephemeral:");
    assertThat(key1).isNotEqualTo(key2);
  }

  @Test
  void channelKeyUsesPrefix() {
    assertThat(eventLog.channelKey("orders")).isEqualTo("channel:orders");
  }

  @Test
  void broadcastKeyUsesPrefix() {
    assertThat(eventLog.broadcastKey("announcements")).isEqualTo("broadcast:announcements");
  }

  private static OdysseyEvent buildEvent(String streamKey, String eventType, String payload) {
    return OdysseyEvent.builder()
        .streamKey(streamKey)
        .eventType(eventType)
        .payload(payload)
        .timestamp(Instant.now())
        .build();
  }
}
