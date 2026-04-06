# Implement PubSubNotificationListener

## What to build

Create the `PubSubNotificationListener` class in `odyssey-redis`. This is a single
virtual thread per node that subscribes to `odyssey:notify:*` via Redis Pub/Sub and
dispatches nudges to the appropriate `TopicFanout`.

**Responsibilities:**
- Subscribe to `odyssey:notify:*` using `PSUBSCRIBE` on a dedicated Lettuce Pub/Sub
  connection (Connection 1)
- On message received on channel `odyssey:notify:<streamKey>`:
  1. Extract `<streamKey>` from the channel name
  2. Look up `TopicFanout` for that stream key in a shared registry/map
  3. If found and has subscribers → call `topicFanout.nudgeAll()`
  4. If not found → ignore (no local subscribers on this node)
- No Redis I/O beyond the PSUBSCRIBE — just a map lookup and semaphore releases

**TopicFanout registry:**
- Maintain a `ConcurrentHashMap<String, TopicFanout>` mapping stream keys to their fanouts
- Provide methods to register/unregister fanouts (called when streams are created/closed)
- This map is shared between the listener and the stream registry — decide where it lives
  (likely on the `RedisOdysseyStreamRegistry` or a shared component)

## Acceptance criteria

- [ ] `PubSubNotificationListener` class exists at `org.jwcarman.odyssey.redis.PubSubNotificationListener`
- [ ] Subscribes to `odyssey:notify:*` pattern via Lettuce Pub/Sub
- [ ] Correctly extracts stream key from channel name
- [ ] Looks up `TopicFanout` and calls `nudgeAll()` when subscribers exist
- [ ] Ignores notifications for streams with no local subscribers
- [ ] Runs on a virtual thread
- [ ] Unit tests cover: notification dispatches nudge, unknown stream key ignored,
      stream key extraction from channel name
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Use Lettuce's `StatefulRedisPubSubConnection` and `RedisPubSubListener` interface.
- The listener should be started as a managed lifecycle component (implements
  `SmartLifecycle` or similar) so it starts/stops with the Spring context.
- The stream prefix (`odyssey:`) is configurable — use it when building the pattern and
  when extracting the stream key.
