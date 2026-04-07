# Implement odyssey-eventlog-hazelcast

## What to build

Create the `odyssey-eventlog-hazelcast` module — an `OdysseyEventLog` implementation
backed by Hazelcast `Ringbuffer`.

**Module setup:**
- `artifactId: odyssey-eventlog-hazelcast`
- Depends on `odyssey-core`
- Dependencies: `com.hazelcast:hazelcast` (Spring Boot manages the version)

**Ringbuffer design:**
- One `Ringbuffer` per stream key
- Ringbuffers are created lazily on first `append`
- Configure capacity and TTL from properties
- Sequence numbers are monotonic and cluster-wide — use as event IDs

**`HazelcastOdysseyEventLog` extends `AbstractOdysseyEventLog`:**
- `append(streamKey, event)`: get or create a `Ringbuffer<String>` for the stream key,
  serialize the `OdysseyEvent` to JSON, `ringbuffer.add(json)`. Return the sequence
  number as a string.
- `readAfter(streamKey, lastId)`: parse `lastId` as a sequence number,
  `ringbuffer.readManyAsync(lastId + 1, 0, batchSize, null).toCompletableFuture().join()`.
  Deserialize each item back to `OdysseyEvent`. Return as `Stream<OdysseyEvent>`.
- `readLast(streamKey, count)`: get `ringbuffer.tailSequence()`, calculate start
  sequence as `max(headSequence, tailSequence - count + 1)`, read from there.
  Return as `Stream<OdysseyEvent>`.
- `delete(streamKey)`: `ringbuffer.destroy()`.

**Serialization:**
- Store events as JSON strings in the Ringbuffer using Jackson `ObjectMapper`.
- Take `ObjectMapper` at construction time (same one auto-configured by Spring Boot).

**`HazelcastEventLogAutoConfiguration`:**
- `@AutoConfiguration(before = OdysseyAutoConfiguration.class)`
- `@ConditionalOnClass(HazelcastInstance.class)`
- Creates a `HazelcastOdysseyEventLog` bean using the `HazelcastInstance` and
  `ObjectMapper` beans from the Spring context
- `@PropertySource("classpath:odyssey-eventlog-hazelcast-defaults.properties")`
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Properties (record):**
```properties
odyssey.eventlog.hazelcast.ephemeral-prefix=odyssey:ephemeral:
odyssey.eventlog.hazelcast.channel-prefix=odyssey:channel:
odyssey.eventlog.hazelcast.broadcast-prefix=odyssey:broadcast:
odyssey.eventlog.hazelcast.ringbuffer-capacity=100000
odyssey.eventlog.hazelcast.ringbuffer-ttl=1h
```

**Ringbuffer configuration:**
- Configure TTL and capacity via `RingbufferConfig` on the Hazelcast instance
  at startup, or use a wildcard config pattern matching the stream prefix.
- If the Ringbuffer doesn't exist yet, Hazelcast creates it automatically when
  you call `hazelcast.getRingbuffer(name)`.

## Acceptance criteria

- [ ] `odyssey-eventlog-hazelcast` module exists with correct POM
- [ ] `HazelcastOdysseyEventLog` extends `AbstractOdysseyEventLog`
- [ ] `append` stores serialized event in Ringbuffer and returns sequence as ID
- [ ] `readAfter` returns events after the given sequence in order
- [ ] `readLast` returns last N events in chronological order
- [ ] `delete` destroys the Ringbuffer
- [ ] Properties are a record with defaults file
- [ ] Auto-configuration registers the bean when Hazelcast is on the classpath
- [ ] License headers on all files
- [ ] Integration tests with embedded Hazelcast (no Testcontainers needed —
      Hazelcast runs embedded in the JVM)
- [ ] Tests named `*IT` if using embedded Hazelcast instance, `*Test` if pure mocks
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Hazelcast `Ringbuffer` is bounded and circular — old events are evicted when
  capacity is reached. This matches our `maxLen` concept naturally.
- Ringbuffer TTL evicts entries older than the configured duration — matches our
  stream TTL concept.
- Sequence numbers are `long` values, cluster-wide monotonic. Convert to/from
  String for the event ID.
- `readManyAsync` returns a `CompletionStage` — call `.toCompletableFuture().join()`
  since we're on virtual threads and blocking is fine.
- Embedded Hazelcast: for tests, just create a `HazelcastInstance` with
  `Hazelcast.newHazelcastInstance()`. No Docker, no Testcontainers.
- Spring Boot auto-configures `HazelcastInstance` if it's on the classpath and
  finds a `hazelcast.xml` or `hazelcast.yaml` config. For our tests, create the
  instance programmatically.
