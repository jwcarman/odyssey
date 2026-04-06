# Implement odyssey-notifier-postgresql

## What to build

Create the `odyssey-notifier-postgresql` module — an `OdysseyStreamNotifier` implementation
backed by PostgreSQL `LISTEN`/`NOTIFY`.

**Module setup:**
- `artifactId: odyssey-notifier-postgresql`
- Depends on `odyssey-core`
- Dependencies: `spring-boot-starter-jdbc`, PostgreSQL JDBC driver

**`PostgresOdysseyStreamNotifier` implements `OdysseyStreamNotifier`:**

- `notify(streamKey, eventId)`: execute `SELECT pg_notify('odyssey_notify',
  '<streamKey>|<eventId>')`. The payload is a simple delimited string containing both
  the stream key and event ID.
- `subscribe(pattern, handler)`: start a dedicated virtual thread that holds a raw JDBC
  connection and issues `LISTEN odyssey_notify`. Poll for notifications via
  `PGConnection.getNotifications(timeout)`. On each notification, parse the payload to
  extract `streamKey` and `eventId`, check if the stream key matches the pattern, and
  call `handler.onNotification(streamKey, eventId)`.

**Pattern matching:**
- PostgreSQL LISTEN doesn't support pattern-based channels like Redis PSUBSCRIBE.
- Use a single channel (`odyssey_notify`) for all notifications.
- Pattern matching happens client-side — the listener checks if the stream key matches
  the registered pattern before dispatching.

**Connection management:**
- The LISTEN connection MUST be a dedicated, long-lived raw JDBC connection — not from
  the connection pool. `LISTEN` is session-scoped.
- Obtain via `DataSource.getConnection()` and hold it for the lifetime of the application.
- Implement `SmartLifecycle` to start/stop the listener thread and close the connection.

## Acceptance criteria

- [ ] `odyssey-notifier-postgresql` module exists with correct POM
- [ ] `PostgresOdysseyStreamNotifier` implements `OdysseyStreamNotifier`
- [ ] `notify` sends a `pg_notify` with stream key and event ID
- [ ] `subscribe` starts a LISTEN loop on a dedicated connection
- [ ] Notifications are dispatched to the handler with correct stream key and event ID
- [ ] Pattern matching works client-side
- [ ] Listener starts/stops with the application lifecycle via `SmartLifecycle`
- [ ] Dedicated connection is properly closed on shutdown
- [ ] Integration tests with Testcontainers PostgreSQL
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- PostgreSQL NOTIFY payload is limited to 8000 bytes — more than enough for a stream
  key + event ID.
- Use `PGConnection` (the PostgreSQL-specific JDBC interface) to access `getNotifications()`.
  Cast from the raw connection: `connection.unwrap(PGConnection.class)`.
- The poll timeout on `getNotifications` serves as the equivalent of our keep-alive
  interval — use `keepAliveInterval` in milliseconds.
- This pairs naturally with `odyssey-eventlog-postgresql` for an all-PostgreSQL stack
  with zero additional infrastructure.
