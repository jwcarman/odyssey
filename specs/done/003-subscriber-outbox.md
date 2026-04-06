# Implement SubscriberOutbox

## What to build

Create the `SubscriberOutbox` class in `odyssey-redis`. This is the per-subscriber
coordination object that bridges Redis reads to SSE writes via two virtual threads and a
`BlockingQueue`.

**Architecture:**
```
[Reader Thread] → BlockingQueue<OdysseyEvent> → [Writer Thread] → SseEmitter
```

**Components:**
- `Semaphore nudge` — Pub/Sub listener releases to wake the reader. Reader acquires.
- `BlockingQueue<OdysseyEvent> queue` — reader offers events, writer polls.
- `String lastReadId` — only touched by the reader thread. No concurrency concerns.
- Reader virtual thread and writer virtual thread.

**Reader thread loop:**
1. `nudge.tryAcquire(keepAliveInterval, MILLISECONDS)`
2. `nudge.drainPermits()` — coalesce piled-up nudges
3. Non-blocking `XREAD` from `lastReadId` using a provided Redis commands callback/function
4. Offer events to `queue`, update `lastReadId`
5. Repeat

**Writer thread loop:**
1. `queue.poll(keepAliveInterval, MILLISECONDS)`
2. If `null` → send keep-alive comment to `SseEmitter`, repeat
3. If `POISON` sentinel → close `SseEmitter`, exit
4. Otherwise → convert `OdysseyEvent` to SSE event (set `id`, `event`, `data`), send to
   `SseEmitter`, repeat

**Shutdown:**
- Graceful (`closeGracefully`): interrupt reader, offer `POISON` to queue. Writer drains
  remaining events, hits poison, closes emitter, exits.
- Immediate (`closeImmediately`): interrupt both threads.

**POISON sentinel:**
- Define as a static final `OdysseyEvent` constant on `SubscriberOutbox` (package-private).
- Use a sentinel ID like `"__poison__"` — it will never match a real Redis Stream ID.

## Acceptance criteria

- [ ] `SubscriberOutbox` class exists at `org.jwcarman.odyssey.redis.SubscriberOutbox`
- [ ] `nudge()` method releases the semaphore
- [ ] Reader thread wakes on nudge and performs read via provided callback
- [ ] Reader thread wakes on timeout (keep-alive interval) and performs read
- [ ] `drainPermits()` is called after acquire so piled-up nudges result in one read
- [ ] Writer thread sends events to `SseEmitter` with correct `id`, `event`, `data` fields
- [ ] Writer thread sends keep-alive comment on poll timeout
- [ ] `closeGracefully()` interrupts reader, poisons queue, writer drains and closes emitter
- [ ] `closeImmediately()` interrupts both threads
- [ ] Unit tests cover: nudge wakes reader, timeout wakes reader, graceful shutdown drains
      events, immediate shutdown stops both threads
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- The reader thread needs access to Redis commands to perform `XREAD`. Accept this as a
  functional interface (e.g., `Function<String, List<OdysseyEvent>>`) passed at construction
  time, so the outbox doesn't depend directly on Lettuce types. This also makes unit testing
  easier — pass a mock/stub function.
- The writer thread needs access to the `SseEmitter`. Pass it at construction time.
- Use `Thread.ofVirtual().name("odyssey-reader-" + streamKey).start(...)` for observability.
- The `SseEmitter` timeout should be set when creating it (before passing to the outbox).
