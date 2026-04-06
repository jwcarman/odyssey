# Implement odyssey-eventlog-postgresql

## What to build

Create the `odyssey-eventlog-postgresql` module — an `OdysseyEventLog` implementation
backed by a PostgreSQL table.

**Module setup:**
- `artifactId: odyssey-eventlog-postgresql`
- Depends on `odyssey-core`
- Dependencies: `spring-boot-starter-jdbc` (or `spring-boot-starter-data-jpa` — prefer
  plain JDBC for simplicity)

**Schema:**

```sql
CREATE TABLE odyssey_events (
    id          BIGSERIAL PRIMARY KEY,
    stream_key  VARCHAR(512) NOT NULL,
    event_type  VARCHAR(256) NOT NULL,
    payload     TEXT NOT NULL,
    timestamp   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata    JSONB DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_odyssey_events_stream_key_id ON odyssey_events (stream_key, id);
```

Use `BIGSERIAL` for ordered IDs — monotonically increasing, comparable, simple. The event
ID returned to callers is the stringified `BIGSERIAL` value.

**`PostgresOdysseyEventLog` implements `OdysseyEventLog`:**
- `append(streamKey, event)`: `INSERT INTO odyssey_events (...) VALUES (...)
  RETURNING id`. Return the ID as a string. Trim old events if count exceeds `maxLen`
  (periodic `DELETE` or on append).
- `readAfter(streamKey, lastId)`: `SELECT ... WHERE stream_key = ? AND id > ?
  ORDER BY id`. Return as `Stream<OdysseyEvent>`.
- `readLast(streamKey, count)`: subquery or `ORDER BY id DESC LIMIT ? ` reversed.
  Return as `Stream<OdysseyEvent>`.
- `delete(streamKey)`: `DELETE FROM odyssey_events WHERE stream_key = ?`.

**`PostgresEventLogAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass(DataSource.class)`
- `@ConditionalOnProperty(name = "odyssey.eventlog.type", havingValue = "postgresql")`
  — needed to disambiguate from other JDBC-based backends
- Creates a `PostgresOdysseyEventLog` bean
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Schema management:**
- Provide the schema as a resource at `db/odyssey/postgresql/V1__create_events.sql`
- Document that consumers should apply it via Flyway/Liquibase or manually
- Optionally: auto-create the table on startup if configured
  (`odyssey.eventlog.postgresql.auto-create-schema: true`)

## Acceptance criteria

- [ ] `odyssey-eventlog-postgresql` module exists with correct POM
- [ ] `PostgresOdysseyEventLog` implements `OdysseyEventLog`
- [ ] `append` inserts and returns a monotonic ID
- [ ] `readAfter` returns events after the given ID in order
- [ ] `readLast` returns last N events in chronological order
- [ ] `delete` removes all events for a stream key
- [ ] Old events are trimmed when exceeding `maxLen`
- [ ] Auto-configuration registers the bean correctly
- [ ] Schema SQL is provided as a resource
- [ ] Integration tests with Testcontainers PostgreSQL
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Use `JdbcTemplate` — no need for JPA/Hibernate for this.
- `metadata` stored as JSONB enables flexible querying if needed later.
- Trimming strategy: consider a periodic cleanup rather than on every append to avoid
  extra DELETE on the hot path. A scheduled task or "every Nth append" approach works.
- The `@ConditionalOnProperty` disambiguates this from other backends that might also
  have a `DataSource` on the classpath.
