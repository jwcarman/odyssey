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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.AbstractOdysseyEventLog;

public class CassandraOdysseyEventLog extends AbstractOdysseyEventLog {

  private final CqlSession session;
  private final int defaultTtlSeconds;

  private final PreparedStatement insertStatement;
  private final PreparedStatement insertWithTtlStatement;
  private final PreparedStatement readAfterStatement;
  private final PreparedStatement readLastStatement;
  private final PreparedStatement deleteStatement;

  public CassandraOdysseyEventLog(
      CqlSession session,
      int defaultTtlSeconds,
      String ephemeralPrefix,
      String channelPrefix,
      String broadcastPrefix) {
    super(ephemeralPrefix, channelPrefix, broadcastPrefix);
    this.session = session;
    this.defaultTtlSeconds = defaultTtlSeconds;

    this.insertStatement =
        session.prepare(
            "INSERT INTO odyssey_events (stream_key, event_id, event_type, payload, timestamp,"
                + " metadata) VALUES (?, ?, ?, ?, ?, ?)");

    this.insertWithTtlStatement =
        session.prepare(
            "INSERT INTO odyssey_events (stream_key, event_id, event_type, payload, timestamp,"
                + " metadata) VALUES (?, ?, ?, ?, ?, ?) USING TTL ?");

    this.readAfterStatement =
        session.prepare(
            "SELECT event_id, stream_key, event_type, payload, timestamp, metadata"
                + " FROM odyssey_events WHERE stream_key = ? AND event_id > ? ORDER BY event_id"
                + " ASC");

    this.readLastStatement =
        session.prepare(
            "SELECT event_id, stream_key, event_type, payload, timestamp, metadata"
                + " FROM odyssey_events WHERE stream_key = ? ORDER BY event_id DESC LIMIT ?");

    this.deleteStatement =
        session.prepare("DELETE FROM odyssey_events WHERE stream_key = ?");
  }

  @Override
  public String append(String streamKey, OdysseyEvent event) {
    UUID eventId = Uuids.timeBased();
    Map<String, String> metadata =
        event.metadata() != null ? new HashMap<>(event.metadata()) : new HashMap<>();

    if (defaultTtlSeconds > 0) {
      session.execute(
          insertWithTtlStatement.bind(
              streamKey,
              eventId,
              event.eventType(),
              event.payload(),
              event.timestamp(),
              metadata,
              defaultTtlSeconds));
    } else {
      session.execute(
          insertStatement.bind(
              streamKey,
              eventId,
              event.eventType(),
              event.payload(),
              event.timestamp(),
              metadata));
    }

    return eventId.toString();
  }

  @Override
  public Stream<OdysseyEvent> readAfter(String streamKey, String lastId) {
    UUID lastUuid = UUID.fromString(lastId);
    ResultSet rs = session.execute(readAfterStatement.bind(streamKey, lastUuid));

    List<OdysseyEvent> events = new ArrayList<>();
    for (Row row : rs) {
      events.add(mapRow(row));
    }
    return events.stream();
  }

  @Override
  public Stream<OdysseyEvent> readLast(String streamKey, int count) {
    ResultSet rs = session.execute(readLastStatement.bind(streamKey, count));

    List<OdysseyEvent> events = new ArrayList<>();
    for (Row row : rs) {
      events.add(mapRow(row));
    }
    Collections.reverse(events);
    return events.stream();
  }

  @Override
  public void delete(String streamKey) {
    session.execute(deleteStatement.bind(streamKey));
  }

  private OdysseyEvent mapRow(Row row) {
    Map<String, String> metadata = row.getMap("metadata", String.class, String.class);
    return OdysseyEvent.builder()
        .id(row.getUuid("event_id").toString())
        .streamKey(row.getString("stream_key"))
        .eventType(row.getString("event_type"))
        .payload(row.getString("payload"))
        .timestamp(row.getInstant("timestamp"))
        .metadata(metadata != null ? metadata : Map.of())
        .build();
  }
}
