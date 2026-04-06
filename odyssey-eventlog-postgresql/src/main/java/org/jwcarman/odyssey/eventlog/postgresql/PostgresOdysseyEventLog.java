package org.jwcarman.odyssey.eventlog.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostgresOdysseyEventLog implements OdysseyEventLog {

  private static final int TRIM_INTERVAL = 100;

  private final JdbcTemplate jdbcTemplate;
  private final long maxLen;
  private final String ephemeralPrefix;
  private final String channelPrefix;
  private final String broadcastPrefix;
  private final AtomicLong appendCounter = new AtomicLong(0);

  public PostgresOdysseyEventLog(
      JdbcTemplate jdbcTemplate,
      long maxLen,
      String ephemeralPrefix,
      String channelPrefix,
      String broadcastPrefix) {
    this.jdbcTemplate = jdbcTemplate;
    this.maxLen = maxLen;
    this.ephemeralPrefix = ephemeralPrefix;
    this.channelPrefix = channelPrefix;
    this.broadcastPrefix = broadcastPrefix;
  }

  @Override
  public String ephemeralKey() {
    return ephemeralPrefix + UUID.randomUUID();
  }

  @Override
  public String channelKey(String name) {
    return channelPrefix + name;
  }

  @Override
  public String broadcastKey(String name) {
    return broadcastPrefix + name;
  }

  @Override
  public String append(String streamKey, OdysseyEvent event) {
    String metadataJson = toJsonb(event.metadata());

    Long id =
        jdbcTemplate.queryForObject(
            "INSERT INTO odyssey_events (stream_key, event_type, payload, timestamp, metadata)"
                + " VALUES (?, ?, ?, ?, ?::jsonb) RETURNING id",
            Long.class,
            streamKey,
            event.eventType(),
            event.payload(),
            java.sql.Timestamp.from(event.timestamp()),
            metadataJson);

    if (appendCounter.incrementAndGet() % TRIM_INTERVAL == 0) {
      trimOldEvents(streamKey);
    }

    return String.valueOf(id);
  }

  @Override
  public Stream<OdysseyEvent> readAfter(String streamKey, String lastId) {
    long cursor = Long.parseLong(lastId);
    List<OdysseyEvent> events =
        jdbcTemplate.query(
            "SELECT id, stream_key, event_type, payload, timestamp, metadata"
                + " FROM odyssey_events WHERE stream_key = ? AND id > ? ORDER BY id",
            this::mapRow,
            streamKey,
            cursor);
    return events.stream();
  }

  @Override
  public Stream<OdysseyEvent> readLast(String streamKey, int count) {
    List<OdysseyEvent> events =
        jdbcTemplate.query(
            "SELECT id, stream_key, event_type, payload, timestamp, metadata"
                + " FROM odyssey_events WHERE stream_key = ?"
                + " ORDER BY id DESC LIMIT ?",
            this::mapRow,
            streamKey,
            count);
    Collections.reverse(events);
    return events.stream();
  }

  @Override
  public void delete(String streamKey) {
    jdbcTemplate.update("DELETE FROM odyssey_events WHERE stream_key = ?", streamKey);
  }

  private void trimOldEvents(String streamKey) {
    jdbcTemplate.update(
        "DELETE FROM odyssey_events WHERE stream_key = ? AND id NOT IN"
            + " (SELECT id FROM odyssey_events WHERE stream_key = ? ORDER BY id DESC LIMIT ?)",
        streamKey,
        streamKey,
        maxLen);
  }

  private OdysseyEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
    Map<String, String> metadata = parseJsonb(rs.getString("metadata"));
    return OdysseyEvent.builder()
        .id(String.valueOf(rs.getLong("id")))
        .streamKey(rs.getString("stream_key"))
        .eventType(rs.getString("event_type"))
        .payload(rs.getString("payload"))
        .timestamp(rs.getTimestamp("timestamp").toInstant())
        .metadata(metadata)
        .build();
  }

  private static String toJsonb(Map<String, String> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return "{}";
    }
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, String> entry : metadata.entrySet()) {
      if (!first) {
        sb.append(",");
      }
      sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
      sb.append("\"").append(escapeJson(entry.getValue())).append("\"");
      first = false;
    }
    sb.append("}");
    return sb.toString();
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static Map<String, String> parseJsonb(String json) {
    if (json == null || json.equals("{}") || json.isBlank()) {
      return Map.of();
    }
    // Simple JSON object parser for flat string-to-string maps
    Map<String, String> result = new LinkedHashMap<>();
    String content = json.substring(1, json.length() - 1).trim();
    if (content.isEmpty()) {
      return Map.of();
    }
    int i = 0;
    while (i < content.length()) {
      i = skipWhitespace(content, i);
      if (i >= content.length()) break;
      String key = readJsonString(content, i);
      i += key.length() + 2; // skip quotes
      i = skipWhitespace(content, i);
      i++; // skip colon
      i = skipWhitespace(content, i);
      String value = readJsonString(content, i);
      i += value.length() + 2; // skip quotes
      i = skipWhitespace(content, i);
      if (i < content.length() && content.charAt(i) == ',') {
        i++;
      }
      result.put(unescapeJson(key), unescapeJson(value));
    }
    return result.isEmpty() ? Map.of() : result;
  }

  private static int skipWhitespace(String s, int i) {
    while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
      i++;
    }
    return i;
  }

  private static String readJsonString(String content, int start) {
    // start should point to opening quote
    int i = start + 1;
    StringBuilder sb = new StringBuilder();
    while (i < content.length()) {
      char c = content.charAt(i);
      if (c == '\\' && i + 1 < content.length()) {
        sb.append(content.charAt(i + 1));
        i += 2;
      } else if (c == '"') {
        break;
      } else {
        sb.append(c);
        i++;
      }
    }
    return sb.toString();
  }

  private static String unescapeJson(String value) {
    return value.replace("\\\"", "\"").replace("\\\\", "\\");
  }
}
