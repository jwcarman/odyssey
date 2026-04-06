# Implement odyssey-notifier-redis

## What to build

Create the `odyssey-notifier-redis` module — an `OdysseyStreamNotifier` implementation
backed by Redis Pub/Sub.

**Module setup:**
- `artifactId: odyssey-notifier-redis`
- Depends on `odyssey-core` (for the SPI interface)
- Dependencies: `spring-data-redis`, `lettuce-core`

**`RedisOdysseyStreamNotifier` implements `OdysseyStreamNotifier`:**
- `notify(streamKey, eventId)`: `PUBLISH odyssey:notify:<streamKey> <eventId>` on the
  shared Lettuce connection (Connection 2). Fire-and-forget.
- `subscribe(pattern, handler)`: register the handler. On startup, issue
  `PSUBSCRIBE <pattern>` on a dedicated Pub/Sub connection (Connection 1). When a message
  arrives, extract the stream key from the channel name and the event ID from the payload,
  then call `handler.onNotification(streamKey, eventId)`.

**`RedisNotifierAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass(RedisConnectionFactory.class)`
- Creates a `RedisOdysseyStreamNotifier` bean with:
  - A dedicated `StatefulRedisPubSubConnection` (Connection 1) for PSUBSCRIBE
  - The shared `StatefulRedisConnection` (Connection 2) for PUBLISH
- Implements `SmartLifecycle` to start/stop the PSUBSCRIBE subscription with the
  application context
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## Acceptance criteria

- [ ] `odyssey-notifier-redis` module exists with correct POM
- [ ] `RedisOdysseyStreamNotifier` implements `OdysseyStreamNotifier`
- [ ] `notify` performs PUBLISH with the event ID as payload
- [ ] `subscribe` issues PSUBSCRIBE on the dedicated connection
- [ ] Incoming Pub/Sub messages are dispatched to the registered handler
- [ ] Stream key is correctly extracted from the channel name
- [ ] Auto-configuration registers the bean when Redis is on the classpath
- [ ] Presence of this bean prevents core's in-memory fallback from activating
- [ ] PSUBSCRIBE starts and stops with the application lifecycle
- [ ] Uses two distinct Lettuce connections (Pub/Sub + shared)
- [ ] Unit tests cover: notify sends PUBLISH, subscribe dispatches to handler,
      stream key extraction
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- The Pub/Sub connection MUST be separate from the shared connection — PSUBSCRIBE blocks
  the connection for the lifetime of the subscription.
- Use Lettuce's `RedisPubSubListener` interface for receiving messages.
- The stream prefix (`odyssey:`) is configurable via `OdysseyProperties` — use it when
  building the PUBLISH channel name and when extracting the stream key.
