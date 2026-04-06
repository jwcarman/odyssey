# Implement odyssey-notifier-nats

## What to build

Create the `odyssey-notifier-nats` module — an `OdysseyStreamNotifier` implementation
backed by NATS.

**Module setup:**
- `artifactId: odyssey-notifier-nats`
- Depends on `odyssey-core`
- Dependencies: `io.nats:jnats`

**NATS subject mapping:**
- Map stream keys to NATS subjects: `odyssey.notify.<streamKey>` (dots instead of colons)
- Pattern `odyssey:notify:*` maps to NATS wildcard: `odyssey.notify.>`

**`NatsOdysseyStreamNotifier` implements `OdysseyStreamNotifier`:**
- `notify(streamKey, eventId)`: `connection.publish("odyssey.notify.<streamKey>",
  eventId.getBytes())`. Fire-and-forget, no acknowledgment needed.
- `subscribe(pattern, handler)`: `connection.subscribe("odyssey.notify.>")` with a
  `MessageHandler` that extracts the stream key from the subject and the event ID from
  the message data, then calls `handler.onNotification(streamKey, eventId)`.

**`NatsNotifierAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass(Connection.class)` (io.nats.client.Connection)
- Creates a `NatsOdysseyStreamNotifier` bean using a NATS `Connection`
- The NATS connection can be auto-configured via Spring properties or provided as a
  bean by the consumer
- Implement `SmartLifecycle` to manage connection lifecycle
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Configuration:**
```yaml
odyssey:
  notifier:
    nats:
      url: nats://localhost:4222
```

## Acceptance criteria

- [ ] `odyssey-notifier-nats` module exists with correct POM
- [ ] `NatsOdysseyStreamNotifier` implements `OdysseyStreamNotifier`
- [ ] `notify` publishes to the correct NATS subject with event ID as payload
- [ ] `subscribe` receives messages and dispatches to the handler
- [ ] Stream key is correctly extracted from the NATS subject
- [ ] Auto-configuration registers the bean when NATS client is on the classpath
- [ ] Connection lifecycle managed via `SmartLifecycle`
- [ ] Integration tests with Testcontainers NATS
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- NATS is extremely lightweight — the `jnats` client is a single dependency with no
  transitive deps.
- NATS subjects use dots as delimiters and `>` as the multi-level wildcard (vs Redis's
  `*`). Map colons in stream keys to dots for NATS subjects.
- NATS core (not JetStream) is fire-and-forget — exactly what we need for notifications.
  Do not use JetStream for this; it adds unnecessary persistence and overhead.
- Testcontainers supports NATS via `GenericContainer("nats:latest")`.
