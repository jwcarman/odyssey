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
package org.jwcarman.odyssey.eventlog.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class PostgresOdysseyEventLogTest {

  private static final int TRIM_INTERVAL = 100;

  @Mock private JdbcTemplate jdbcTemplate;

  private PostgresOdysseyEventLog eventLog;

  @BeforeEach
  void setUp() {
    eventLog =
        new PostgresOdysseyEventLog(jdbcTemplate, 50L, "ephemeral:", "channel:", "broadcast:");
  }

  private OdysseyEvent buildEvent(String streamKey) {
    return OdysseyEvent.builder()
        .streamKey(streamKey)
        .eventType("test.event")
        .payload("payload")
        .timestamp(Instant.now())
        .build();
  }

  private OdysseyEvent buildEvent(String streamKey, Map<String, String> metadata) {
    return OdysseyEvent.builder()
        .streamKey(streamKey)
        .eventType("test.event")
        .payload("payload")
        .timestamp(Instant.now())
        .metadata(metadata)
        .build();
  }

  // ---------------------------------------------------------------------------
  // trimOldEvents() — triggered on every 100th append
  // ---------------------------------------------------------------------------

  @Test
  void trimOldEventsIsCalledOnHundredthAppend() {
    // Each append calls jdbcTemplate.queryForObject for the INSERT … RETURNING id.
    // Return a monotonically increasing id so the result is non-null.
    when(jdbcTemplate.queryForObject(
            anyString(), eq(Long.class), any(), any(), any(), any(), any()))
        .thenAnswer(inv -> (long) (System.nanoTime() % 1_000_000));

    String streamKey = "channel:trimtest";
    OdysseyEvent event = buildEvent(streamKey);

    for (int i = 0; i < TRIM_INTERVAL; i++) {
      eventLog.append(streamKey, event);
    }

    // Verify that the DELETE … NOT IN … trim SQL ran at least once.
    verify(jdbcTemplate, atLeastOnce())
        .update(
            contains("DELETE FROM odyssey_events WHERE stream_key = ? AND id NOT IN"),
            eq(streamKey),
            eq(streamKey),
            eq(50L));
  }

  // ---------------------------------------------------------------------------
  // toJsonb() — indirectly via append()
  // null metadata → INSERT receives "{}"
  // empty metadata → INSERT receives "{}"
  // valid metadata → INSERT receives JSON-encoded string
  // ---------------------------------------------------------------------------

  @Test
  void appendUsesEmptyJsonWhenMetadataIsNull() {
    when(jdbcTemplate.queryForObject(
            anyString(), eq(Long.class), any(), any(), any(), any(), any()))
        .thenReturn(1L);

    OdysseyEvent event =
        OdysseyEvent.builder()
            .streamKey("channel:test")
            .eventType("evt")
            .payload("p")
            .timestamp(Instant.now())
            .metadata(null)
            .build();

    eventLog.append("channel:test", event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(jdbcTemplate)
        .queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), captor.capture());

    // The last vararg is the metadataJson argument (::jsonb param)
    assertThat(captor.getValue()).isEqualTo("{}");
  }

  @Test
  void appendUsesEmptyJsonWhenMetadataIsEmpty() {
    when(jdbcTemplate.queryForObject(
            anyString(), eq(Long.class), any(), any(), any(), any(), any()))
        .thenReturn(1L);

    OdysseyEvent event = buildEvent("channel:test", Map.of());
    eventLog.append("channel:test", event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(jdbcTemplate)
        .queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), captor.capture());

    assertThat(captor.getValue()).isEqualTo("{}");
  }

  @Test
  void appendSerializesMetadataToJson() {
    when(jdbcTemplate.queryForObject(
            anyString(), eq(Long.class), any(), any(), any(), any(), any()))
        .thenReturn(1L);

    OdysseyEvent event = buildEvent("channel:test", Map.of("key", "value"));
    eventLog.append("channel:test", event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(jdbcTemplate)
        .queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), captor.capture());

    assertThat((String) captor.getValue()).contains("\"key\"").contains("\"value\"");
  }

  // ---------------------------------------------------------------------------
  // escapeJson() — verified through toJsonb() via append()
  // Values with backslash and quotes must be escaped.
  // ---------------------------------------------------------------------------

  @Test
  void appendEscapesBackslashAndQuotesInMetadataValues() {
    when(jdbcTemplate.queryForObject(
            anyString(), eq(Long.class), any(), any(), any(), any(), any()))
        .thenReturn(1L);

    OdysseyEvent event = buildEvent("channel:test", Map.of("k", "a\\b\"c"));
    eventLog.append("channel:test", event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(jdbcTemplate)
        .queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), captor.capture());

    String json = (String) captor.getValue();
    assertThat(json).contains("a\\\\b\\\"c");
  }

  // ---------------------------------------------------------------------------
  // parseJsonb() / readJsonString() — indirectly via readAfter() → mapRow()
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private List<OdysseyEvent> stubReadAfter(String metadataJson) throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getLong("id")).thenReturn(1L);
    when(rs.getString("stream_key")).thenReturn("channel:test");
    when(rs.getString("event_type")).thenReturn("evt");
    when(rs.getString("payload")).thenReturn("p");
    when(rs.getTimestamp("timestamp")).thenReturn(Timestamp.from(Instant.now()));
    when(rs.getString("metadata")).thenReturn(metadataJson);

    when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), anyLong()))
        .thenAnswer(
            inv -> {
              RowMapper<OdysseyEvent> mapper = inv.getArgument(1);
              return List.of(mapper.mapRow(rs, 0));
            });

    return eventLog.readAfter("channel:test", "0").toList();
  }

  @Test
  void parseJsonbWithNullReturnsEmptyMap() throws SQLException {
    List<OdysseyEvent> events = stubReadAfter(null);
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().metadata()).isEmpty();
  }

  @Test
  void parseJsonbWithEmptyJsonObjectReturnsEmptyMap() throws SQLException {
    List<OdysseyEvent> events = stubReadAfter("{}");
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().metadata()).isEmpty();
  }

  @Test
  void parseJsonbWithBlankStringReturnsEmptyMap() throws SQLException {
    List<OdysseyEvent> events = stubReadAfter("   ");
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().metadata()).isEmpty();
  }

  @Test
  void parseJsonbWithValidJsonParsesCorrectly() throws SQLException {
    List<OdysseyEvent> events = stubReadAfter("{\"userId\":\"42\",\"source\":\"api\"}");
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().metadata())
        .containsEntry("userId", "42")
        .containsEntry("source", "api");
  }

  @Test
  void readJsonStringHandlesEscapedQuotesInValue() throws SQLException {
    List<OdysseyEvent> events = stubReadAfter("{\"key\":\"say \\\"hello\\\"\"}");
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().metadata()).containsEntry("key", "say \"hello\"");
  }
}
