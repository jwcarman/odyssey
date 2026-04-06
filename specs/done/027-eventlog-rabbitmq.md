# Implement odyssey-eventlog-rabbitmq

## What to build

Create the `odyssey-eventlog-rabbitmq` module — an `OdysseyEventLog` implementation
backed by RabbitMQ Streams (available since RabbitMQ 3.9).

**Module setup:**
- `artifactId: odyssey-eventlog-rabbitmq`
- Depends on `odyssey-core`
- Dependencies: `com.rabbitmq:stream-client` (RabbitMQ Stream Java client)

**RabbitMQ Stream design:**
- One RabbitMQ stream per OdySSEy stream key (e.g., `odyssey.channel.user.123`)
- Streams are append-only logs with offset-based reads — exactly matching the
  `OdysseyEventLog` SPI

**`RabbitMqOdysseyEventLog` implements `OdysseyEventLog`:**
- `append(streamKey, event)`: publish a message to the RabbitMQ stream named after the
  stream key. The message body is the serialized event (JSON). Use the publishing ID
  (sequence number) as the event ID. Return the event ID as a string.
- `readAfter(streamKey, lastId)`: create a stream consumer starting at
  `OffsetSpecification.offset(lastId + 1)`. Read messages and convert to
  `OdysseyEvent`. Return as `Stream<OdysseyEvent>`. Close the consumer when the stream
  is exhausted (no more messages available).
- `readLast(streamKey, count)`: create a stream consumer starting at
  `OffsetSpecification.last()`, read backwards... 

  Actually, RabbitMQ Streams don't support reverse reads. Instead:
  - Query the stream length / last offset
  - Start at `OffsetSpecification.offset(lastOffset - count + 1)`
  - Read forward for `count` messages
  - Return as `Stream<OdysseyEvent>`
- `delete(streamKey)`: delete the RabbitMQ stream.

**Stream creation and retention:**
- Streams are created on first `append` if they don't exist
- Configure retention via RabbitMQ stream policies or at creation time:
  - `maxLengthBytes` — max size
  - `maxAge` — TTL equivalent (e.g., `Duration.ofHours(1)`)
  - `maxSegmentSizeBytes` — segment size for efficient cleanup

**`RabbitMqEventLogAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass` — check for RabbitMQ stream client class
- `@ConditionalOnProperty(name = "odyssey.eventlog.type", havingValue = "rabbitmq")`
- Creates a `RabbitMqOdysseyEventLog` bean
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Configuration:**
```yaml
odyssey:
  eventlog:
    rabbitmq:
      host: localhost
      port: 5552         # RabbitMQ stream protocol port (not AMQP 5672)
      max-age: 1h
      max-length-bytes: 500mb
```

## Acceptance criteria

- [ ] `odyssey-eventlog-rabbitmq` module exists with correct POM
- [ ] `RabbitMqOdysseyEventLog` implements `OdysseyEventLog`
- [ ] `append` publishes to a RabbitMQ stream and returns an event ID
- [ ] Streams are created on first append if they don't exist
- [ ] `readAfter` returns events after the given offset in order
- [ ] `readLast` returns last N events in chronological order
- [ ] `delete` removes the RabbitMQ stream
- [ ] Retention is configured (max age and/or max size)
- [ ] Auto-configuration registers the bean correctly
- [ ] Integration tests with Testcontainers RabbitMQ (with streams enabled)
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- RabbitMQ Streams use a separate protocol (port 5552 by default) from AMQP (5672).
  The `stream-client` library handles this.
- The stream client is different from Spring AMQP — it's a separate dependency
  (`com.rabbitmq:stream-client`). Both can coexist.
- For `readLast`, the lack of reverse reads means we need to calculate the starting
  offset. Use `Environment.queryStreamStats()` to get the last offset, then start at
  `lastOffset - count + 1` (clamped to 0).
- Event serialization: JSON is simplest. Include all `OdysseyEvent` fields in the
  message body.
- Testcontainers: use `new RabbitMQContainer("rabbitmq:3-management")` with the
  `rabbitmq_stream` plugin enabled via
  `.withPluginsEnabled("rabbitmq_stream")`.
- Pairing this with `odyssey-notifier-rabbitmq` gives a single-infrastructure
  RabbitMQ stack using streams for storage and fanout exchanges for notifications.
