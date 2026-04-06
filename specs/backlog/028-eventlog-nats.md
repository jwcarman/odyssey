# Implement odyssey-eventlog-nats

## What to build

Create the `odyssey-eventlog-nats` module — an `OdysseyEventLog` implementation backed
by NATS JetStream.

**Module setup:**
- `artifactId: odyssey-eventlog-nats`
- Depends on `odyssey-core`
- Dependencies: `io.nats:jnats`

**JetStream design:**
- One JetStream stream per OdySSEy stream key prefix (e.g., a stream named `ODYSSEY`
  covering subjects `odyssey.events.>`), or one JetStream stream per OdySSEy stream key
- Subjects: `odyssey.events.<streamKey>` (dots instead of colons)
- Events are published as JetStream messages with the event data as payload
- JetStream assigns a monotonic sequence number per stream — use this as the event ID

**`NatsOdysseyEventLog` implements `OdysseyEventLog`:**
- `append(streamKey, event)`: `jetStream.publish("odyssey.events.<streamKey>",
  serializedEvent)`. Returns the `PublishAck.getSeqno()` as a string. JetStream
  guarantees ordering and persistence.
- `readAfter(streamKey, lastId)`: create a JetStream `PullSubscription` or use
  `fetchMessages` starting at `StartSequence(lastId + 1)` filtered to the subject.
  Read messages, convert to `OdysseyEvent`. Return as `Stream<OdysseyEvent>`.
- `readLast(streamKey, count)`: use `DeliverLastPerSubject` or calculate the starting
  sequence: query stream info for the last sequence, start at `lastSeq - count + 1`.
  Read forward. Return as `Stream<OdysseyEvent>`.
- `delete(streamKey)`: purge messages for the subject via
  `streamManagement.purgeStream(streamName, PurgeOptions.subject(...))`.

**Stream configuration:**
- Create the JetStream stream on startup if it doesn't exist
- Retention: `RetentionPolicy.LIMITS` with `MaxMsgs`, `MaxAge`, or `MaxBytes`
- Storage: `StorageType.FILE` for durability

**`NatsEventLogAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass(Connection.class)` (io.nats.client.Connection)
- `@ConditionalOnProperty(name = "odyssey.eventlog.type", havingValue = "nats")`
  — disambiguate from `odyssey-notifier-nats`
- Creates a `NatsOdysseyEventLog` bean using the NATS connection
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Configuration:**
```yaml
odyssey:
  eventlog:
    nats:
      url: nats://localhost:4222
      stream-name: ODYSSEY
      max-age: 1h
      max-messages: 100000
```

## Acceptance criteria

- [ ] `odyssey-eventlog-nats` module exists with correct POM
- [ ] `NatsOdysseyEventLog` implements `OdysseyEventLog`
- [ ] `append` publishes to JetStream and returns the sequence number as event ID
- [ ] `readAfter` returns events after the given sequence in order
- [ ] `readLast` returns last N events in chronological order
- [ ] `delete` purges messages for the stream key's subject
- [ ] JetStream stream is created on startup if it doesn't exist
- [ ] Retention limits are configured (max age, max messages)
- [ ] Auto-configuration registers the bean correctly
- [ ] Integration tests with Testcontainers NATS (with JetStream enabled)
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- JetStream is enabled by default in NATS server 2.2+. No special configuration needed
  for the Testcontainer.
- The `jnats` client handles both Core NATS and JetStream — same dependency as the
  notifier module. Both modules can share a single NATS connection.
- Pairing this with `odyssey-notifier-nats` gives a single-infrastructure NATS stack:
  JetStream for storage, Core NATS for fire-and-forget notifications.
- JetStream sequence numbers are per-stream (not per-subject), so if using a single
  JetStream stream for all OdySSEy stream keys, sequences will have gaps per subject.
  This is fine — `readAfter` filters by subject and sequences are still ordered.
- For `readLast`, JetStream doesn't have a native "last N for a subject" API. Use
  stream info to get the last sequence, then start a consumer at an estimated offset.
  This may return slightly more than N messages; trim client-side.
- Testcontainers: `new GenericContainer("nats:latest").withCommand("--jetstream")`.
