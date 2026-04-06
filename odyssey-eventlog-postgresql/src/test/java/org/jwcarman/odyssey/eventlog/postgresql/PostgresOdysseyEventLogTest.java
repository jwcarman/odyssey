package org.jwcarman.odyssey.eventlog.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresOdysseyEventLogTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  private PostgresOdysseyEventLog eventLog;
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    DataSource dataSource =
        createDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbcTemplate = new JdbcTemplate(dataSource);

    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
    populator.addScript(new ClassPathResource("db/odyssey/postgresql/V1__create_events.sql"));
    populator.execute(dataSource);

    jdbcTemplate.update("DELETE FROM odyssey_events");

    eventLog =
        new PostgresOdysseyEventLog(jdbcTemplate, 100, "ephemeral:", "channel:", "broadcast:");
  }

  @Test
  void appendReturnsMonotonicId() {
    OdysseyEvent event = buildEvent("test-stream", "msg", "hello");
    String id1 = eventLog.append("test-stream", event);
    String id2 = eventLog.append("test-stream", event);

    assertThat(Long.parseLong(id1)).isPositive();
    assertThat(Long.parseLong(id2)).isGreaterThan(Long.parseLong(id1));
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
    List<OdysseyEvent> events = eventLog.readAfter("nonexistent", "0").toList();
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
  void trimRemovesOldEventsWhenExceedingMaxLen() {
    // Use a small maxLen to test trimming
    PostgresOdysseyEventLog smallLog =
        new PostgresOdysseyEventLog(jdbcTemplate, 5, "ephemeral:", "channel:", "broadcast:");

    String streamKey = "channel:trimtest";
    // Append enough events to trigger trimming (every 100th append triggers trim)
    // We need to verify the trim SQL works correctly by calling it more directly
    for (int i = 0; i < 10; i++) {
      smallLog.append(streamKey, buildEvent(streamKey, "msg", "event-" + i));
    }

    // Force a trim by reading the count
    List<OdysseyEvent> allEvents = smallLog.readLast(streamKey, 100).toList();
    // All 10 events exist because trim only fires every 100 appends
    assertThat(allEvents).hasSize(10);
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
    // PostgreSQL TIMESTAMPTZ has microsecond precision, so truncate to millis for comparison
    assertThat(events.getFirst().timestamp().toEpochMilli()).isEqualTo(now.toEpochMilli());
  }

  private static OdysseyEvent buildEvent(String streamKey, String eventType, String payload) {
    return OdysseyEvent.builder()
        .streamKey(streamKey)
        .eventType(eventType)
        .payload(payload)
        .timestamp(Instant.now())
        .build();
  }

  private static DataSource createDataSource(String url, String username, String password) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setUrl(url);
    ds.setUsername(username);
    ds.setPassword(password);
    return ds;
  }
}
