# Implement TopicFanout

## What to build

Create the `TopicFanout` class in `odyssey-redis`. This is a per-stream-key object that
manages the list of local `SubscriberOutbox` instances and delegates nudges to all of them.

**Responsibilities:**
- Maintain a thread-safe list of `SubscriberOutbox` instances for a single stream key
- `nudgeAll()` — call `nudge()` on every registered outbox
- `addSubscriber(outbox)` — register a new outbox
- `removeSubscriber(outbox)` — unregister an outbox
- `shutdown()` — call `closeGracefully()` on all outboxes
- `hasSubscribers()` — check if any outboxes are registered

**Thread safety:**
- The Pub/Sub listener calls `nudgeAll()` from its thread
- Subscribe/unsubscribe happen from request-handling threads
- Use `CopyOnWriteArrayList` — reads (nudge) are far more frequent than writes
  (subscribe/unsubscribe). Or use a simple synchronized list — the critical section is tiny.

## Acceptance criteria

- [ ] `TopicFanout` class exists at `org.jwcarman.odyssey.redis.TopicFanout`
- [ ] `addSubscriber` registers an outbox
- [ ] `removeSubscriber` unregisters an outbox
- [ ] `nudgeAll` calls `nudge()` on every registered outbox
- [ ] `shutdown` calls `closeGracefully()` on every registered outbox
- [ ] `hasSubscribers` returns correct state
- [ ] Thread-safe for concurrent add/remove/nudge operations
- [ ] Unit tests cover: add/remove lifecycle, nudgeAll reaches all outboxes, shutdown
      gracefully closes all outboxes, hasSubscribers reflects current state
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- This is a simple class. Keep it simple — no over-engineering.
