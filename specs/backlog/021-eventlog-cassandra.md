# Implement odyssey-eventlog-cassandra

## What to build

Create the `odyssey-eventlog-cassandra` module — an `OdysseyEventLog` implementation
backed by Apache Cassandra.

**Module setup:**
- `artifactId: odyssey-eventlog-cassandra`
- Depends on `odyssey-core`
- Dependencies: `spring-boot-starter-data-cassandra` (or `spring-data-cassandra`)

**Schema:**

```cql
CREATE TABLE odyssey_events (
    stream_key  TEXT,
    event_id    TIMEUUID,
    event_type  TEXT,
    payload     TEXT,
    timestamp   TIMESTAMP,
    metadata    MAP<TEXT, TEXT>,
    PRIMARY KEY (stream_key, event_id)
) WITH CLUSTERING ORDER BY (event_id ASC)
  AND default_time_to_live = 86400;
```

Use `TIMEUUID` for event IDs — monotonically increasing, time-based, Cassandra-native.
The event ID returned to callers is the stringified `TIMEUUID`.

**`CassandraOdysseyEventLog` implements `OdysseyEventLog`:**
- `append(streamKey, event)`: `INSERT INTO odyssey_events (...) VALUES (...)` with a
  generated `TIMEUUID` (via `Uuids.timeBased()`). Set TTL per stream type if configured.
  Return the TIMEUUID as a string.
- `readAfter(streamKey, lastId)`: `SELECT ... WHERE stream_key = ? AND event_id > ?
  ORDER BY event_id ASC`. Parse `lastId` back to `UUID`. Return as `Stream<OdysseyEvent>`.
- `readLast(streamKey, count)`: `SELECT ... WHERE stream_key = ?
  ORDER BY event_id DESC LIMIT ?`. Reverse results. Return as `Stream<OdysseyEvent>`.
- `delete(streamKey)`: `DELETE FROM odyssey_events WHERE stream_key = ?`.

**`CassandraEventLogAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass(CqlSession.class)`
- Creates a `CassandraOdysseyEventLog` bean using the `CqlSession` from Spring Data
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**TTL handling:**
- Cassandra supports per-row TTL natively via `USING TTL <seconds>` on INSERT.
- Map the stream type TTL config to Cassandra TTL on each insert.
- TTL=0 means use the table's `default_time_to_live` (or no expiry if table default is 0).

**Trimming:**
- Cassandra handles trimming via TTL — no explicit `DELETE` needed for old events.
- `maxLen` is not directly applicable; TTL is the primary eviction mechanism.

## Acceptance criteria

- [ ] `odyssey-eventlog-cassandra` module exists with correct POM
- [ ] `CassandraOdysseyEventLog` implements `OdysseyEventLog`
- [ ] `append` inserts with TIMEUUID and returns the ID
- [ ] `append` applies TTL when configured
- [ ] `readAfter` returns events after the given ID in order
- [ ] `readLast` returns last N events in chronological order
- [ ] `delete` removes all events for a stream key
- [ ] Auto-configuration registers the bean when Cassandra is on the classpath
- [ ] Integration tests with Testcontainers Cassandra
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Use `CqlSession` directly or Spring Data Cassandra's `CassandraTemplate`.
- TIMEUUID comparison works natively in CQL — no custom comparator needed.
- Cassandra's partition key (stream_key) means all events for a stream are co-located
  on the same node — efficient reads.
- For high-volume streams, consider adding a time bucket to the partition key to avoid
  unbounded partition growth. This is an optimization for later — start simple.
- Schema should be provided as a resource for consumers to apply manually or via a
  migration tool.
