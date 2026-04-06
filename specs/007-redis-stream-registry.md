# Implement RedisOdysseyStreamRegistry

## What to build

Create `RedisOdysseyStreamRegistry` in `odyssey-redis` — the implementation of
`OdysseyStreamRegistry` that creates and manages `RedisOdysseyStream` instances.

**`ephemeral()`:**
- Generate a UUID
- Build stream key: `<prefix>ephemeral:<uuid>`
- Create and return a `RedisOdysseyStream` with ephemeral TTL config
- Set a Redis TTL (`EXPIRE`) on the stream key

**`channel(String name)`:**
- Build stream key: `<prefix>channel:<name>`
- Create and return a `RedisOdysseyStream` with channel TTL config
- Set/refresh Redis TTL on the stream key

**`broadcast(String name)`:**
- Build stream key: `<prefix>broadcast:<name>`
- Create and return a `RedisOdysseyStream` with broadcast TTL config
- Set/refresh Redis TTL on the stream key

**Stream caching:**
- Channel and broadcast streams should be cached by name — calling `channel("user:123")`
  twice should return the same `RedisOdysseyStream` instance (same `TopicFanout`).
- Ephemeral streams are never cached — each call creates a new one.
- Use a `ConcurrentHashMap<String, RedisOdysseyStream>` for the cache.

**TTL refresh:**
- On `publish()`, refresh the TTL so active streams don't expire.
- This could be done in `RedisOdysseyStream.publish()` directly (XADD + PUBLISH + EXPIRE)
  or via a wrapper. Decide at implementation time.

**TopicFanout registry:**
- The registry owns the `ConcurrentHashMap<String, TopicFanout>` shared with the
  `PubSubNotificationListener`.
- When a stream is created, its `TopicFanout` is registered.
- When a stream is closed/deleted, its `TopicFanout` is unregistered.

## Acceptance criteria

- [ ] `RedisOdysseyStreamRegistry` implements `OdysseyStreamRegistry`
- [ ] `ephemeral()` returns a stream with an auto-generated UUID key
- [ ] `channel(name)` returns a stream with key `<prefix>channel:<name>`
- [ ] `broadcast(name)` returns a stream with key `<prefix>broadcast:<name>`
- [ ] Repeated calls to `channel` or `broadcast` with the same name return the same instance
- [ ] Each `ephemeral()` call returns a distinct instance
- [ ] TTL is set on stream keys according to stream type configuration
- [ ] TopicFanout registry is shared with PubSubNotificationListener
- [ ] Unit tests cover: key generation, caching behavior, distinct ephemeral instances
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- The registry needs the shared Lettuce connection (Connection 2), the Pub/Sub listener
  reference (or the shared fanout map), and configuration values.
- Consider whether TTL=0 (broadcast default in some configs) means "no expiry" — if so,
  skip the EXPIRE call.
