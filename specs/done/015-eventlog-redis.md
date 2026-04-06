# Implement odyssey-eventlog-redis

## What to build

Create the `odyssey-eventlog-redis` module — an `OdysseyEventLog` implementation backed
by Redis Streams.

**Module setup:**
- `artifactId: odyssey-eventlog-redis`
- Depends on `odyssey-core` (for the SPI interface)
- Dependencies: `spring-data-redis`, `lettuce-core`

**`RedisOdysseyEventLog` implements `OdysseyEventLog`:**
- `append(streamKey, event)`: `XADD <streamKey> MAXLEN ~ <maxLen> * eventType <type>
  payload <payload> timestamp <ts> [metadata fields...]`. Returns the Redis Stream entry ID.
  Also refreshes TTL via `EXPIRE` based on stream type.
- `readAfter(streamKey, lastId)`: non-blocking `XREAD COUNT <batch> STREAMS <streamKey>
  <lastId>`. Returns results as a `Stream<OdysseyEvent>`.
- `readLast(streamKey, count)`: `XREVRANGE <streamKey> + - COUNT min(count, maxLastN)`.
  Reverse the results and return as a `Stream<OdysseyEvent>`.
- `delete(streamKey)`: `DEL <streamKey>`.

**`RedisEventLogAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass(RedisConnectionFactory.class)`
- Creates a `RedisOdysseyEventLog` bean using a shared `StatefulRedisConnection`
  obtained from the `RedisConnectionFactory`
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Event mapping:**
- `OdysseyEvent` → Redis Stream fields: `eventType`, `payload`, `timestamp`, plus each
  entry in `metadata` as an additional field
- Redis Stream entry → `OdysseyEvent`: reconstruct from fields, using the entry ID as
  `OdysseyEvent.id()` and the stream key as `OdysseyEvent.streamKey()`

## Acceptance criteria

- [ ] `odyssey-eventlog-redis` module exists with correct POM
- [ ] `RedisOdysseyEventLog` implements `OdysseyEventLog`
- [ ] `append` performs XADD with MAXLEN trimming and returns entry ID
- [ ] `append` refreshes TTL via EXPIRE
- [ ] `readAfter` returns events after the given ID as a `Stream`
- [ ] `readLast` returns last N events in chronological order as a `Stream`
- [ ] `delete` removes the Redis key
- [ ] Auto-configuration registers the bean when Redis is on the classpath
- [ ] Presence of this bean prevents core's in-memory fallback from activating
- [ ] Unit tests with mocked Redis commands cover append, readAfter, readLast, delete
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Use Lettuce's synchronous API (`RedisCommands`) — virtual threads handle the blocking.
- The shared connection is thread-safe (Lettuce multiplexes). This is Connection 2 from
  the Redis connection model.
- TTL configuration (per stream type) needs to be accessible — either pass it in at
  construction or accept it as a parameter on `append`. Consider adding a TTL-aware
  overload or a separate `setTtl(streamKey, duration)` method on the SPI if needed.
