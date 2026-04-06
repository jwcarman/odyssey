# PRD — OdySSEy

---

## What this project is

OdySSEy is an open-source Spring Boot library that solves clustered, persistent, resumable
Server-Sent Events. It is the missing piece between raw Spring MVC `SseEmitter` and a
production-ready SSE infrastructure that survives node failures, client reconnects, and
horizontal scaling.

The primary driver is MCP Streamable HTTP transport, where a client may reconnect to a
different node mid-stream and must resume without data loss. OdySSEy generalizes this into
a framework that also supports broadcast and point-to-point delivery patterns.

### Goals

- Zero reactive types. Virtual threads throughout.
- Fixed, small, predictable Redis connection count per node — regardless of stream count or
  subscriber count.
- Three distinct stream types, each implemented optimally for its access pattern, behind a
  single unified API.
- Self-healing via TTL. Explicit close supported.
- Keep-alive / heartbeat on every stream type without additional configuration.
- Spring Boot autoconfiguration — add one starter dependency and two lines of YAML.

### Non-Goals

- WebFlux / reactive support (by design — virtual threads replace this need)
- Multi-broker support (Redis only — not pluggable)
- Cassandra or any secondary store
- Session management (callers own session identity; OdySSEy owns streams)
- Exactly-once delivery guarantees

---

## Tech stack

- Language: Java 25
- Framework: Spring Boot 4.x
- Build tool: Maven (multi-module)
- Data store: Redis (Lettuce client)
- Testing: JUnit 5 + Spring Boot Test
- Linting / formatting: Spotless with Google Java Format
- Package manager: Maven

---

## Maven Coordinates

```
groupId:     org.jwcarman.odyssey
artifactId:  odyssey-parent
version:     1.0.0-SNAPSHOT
Java:        25
Spring Boot: 4.x
```

---

## Module Structure

```
odyssey-parent/
├── odyssey-core/           # Public API, domain model
├── odyssey-redis/          # All three stream type implementations
├── odyssey-autoconfigure/  # Spring Boot AutoConfiguration, properties
└── odyssey-spring-boot-starter/  # Convenience starter (what consumers add)
```

---

## How to run the project

This is a library — there is no standalone application to run. Consumers add the starter
dependency and configure their own Spring Boot application.

```bash
# Compile and package
./mvnw clean install -DskipTests
```

---

## How to run tests

```bash
# Run all tests (unit + integration)
./mvnw verify

# Run a single test class
./mvnw test -pl odyssey-redis -Dtest=SomeTestClass
```

Expected output when all tests pass:
```
[INFO] BUILD SUCCESS
```

---

## How to lint / type-check

```bash
# Check formatting
./mvnw spotless:check

# Auto-fix formatting
./mvnw spotless:apply
```

---

## Coding conventions

- Immutable domain objects (builder pattern)
- No reactive types — virtual threads throughout
- Service layer pattern (thin controllers)
- No `@SuppressWarnings` annotations — fix the underlying issue instead

---

## Repository structure

```
odyssey-parent/
├── odyssey-core/
│   └── src/main/java/io/odyssey/core/
│       ├── OdysseyStream.java              # Public stream interface
│       ├── OdysseyStreamRegistry.java      # Factory interface
│       └── OdysseyEvent.java               # Immutable event record with builder
├── odyssey-redis/
│   └── src/main/java/io/odyssey/redis/
│       ├── RedisOdysseyStreamRegistry.java # Registry implementation
│       ├── RedisOdysseyStream.java         # Unified stream implementation (all three types)
│       ├── SubscriberOutbox.java           # Per-subscriber: semaphore, queue, two virtual threads
│       ├── TopicFanout.java                # Per-stream-key fan-out to local outboxes
│       └── PubSubNotificationListener.java # Single PSUBSCRIBE listener, no I/O
├── odyssey-autoconfigure/
│   └── src/main/java/io/odyssey/autoconfigure/
│       ├── OdysseyAutoConfiguration.java   # Spring Boot auto-config
│       └── OdysseyProperties.java          # @ConfigurationProperties
└── odyssey-spring-boot-starter/
    └── (empty — dependency-only module)
```

---

## Stream Types

OdySSEy exposes three stream types. Each is created via a named factory method on
`OdysseyStreamRegistry`. All three share the same unified implementation pattern — the
differences are configuration (TTL, naming) rather than architecture.

### Unified Implementation Pattern

All stream types use the same publish/subscribe mechanism:

**Publishing:**
1. `XADD` the event to the Redis Stream
2. `PUBLISH` the event's Stream entry ID to `odyssey:notify:<streamKey>` (Pub/Sub wake-up)

The two-command write is not atomic, but that's safe — the worst case is a missed
notification, and the reader thread's `tryAcquire` timeout catches up. No data loss.

**Subscribing — two virtual threads per subscriber:**

Each subscriber gets a `SubscriberOutbox` with two virtual threads and a `BlockingQueue`
between them:

```
[Reader Thread] → BlockingQueue<OdysseyEvent> → [Writer Thread] → SseEmitter
```

**Pub/Sub listener (fast, no I/O):**
1. Receives notification on `odyssey:notify:<streamKey>`
2. Looks up `TopicFanout` for the stream key
   - No local subscribers → ignore
   - Has subscribers → `semaphore.release()` on each outbox (nudge)

**Reader thread (per subscriber):**
```
loop:
    nudge.tryAcquire(keepAliveInterval, MILLISECONDS)
    nudge.drainPermits()    // coalesce piled-up nudges
    events = XREAD from lastReadId (non-blocking)
    for event in events:
        queue.offer(event)
    lastReadId = highest ID returned (if any)
```

- `lastReadId` is only touched by this thread — no concurrency concerns
- Worst case on a redundant nudge: one no-op XREAD (sub-millisecond)
- Shutdown: interrupt the thread. `tryAcquire` throws `InterruptedException`, thread exits.

**Writer thread (per subscriber):**
```
loop:
    event = queue.poll(keepAliveInterval, MILLISECONDS)
    if event == POISON → send remaining events, close SseEmitter, exit
    if event == null → send keep-alive comment, continue
    else → send event to SseEmitter
```

- Handles its own keep-alive via poll timeout
- Shutdown (graceful — `stream.close()`): reader is interrupted, then POISON is offered to
  the queue. Writer drains remaining events, hits POISON, closes the SseEmitter, exits.
- Shutdown (immediate — `stream.delete()` or client disconnect): interrupt both threads.

**Reconnect / replay:**
- `FROM_LAST_EVENT_ID`: `XRANGE` from `lastId`, deliver, then register with `TopicFanout`
- `LAST_N`: `XREVRANGE` limit `min(n, maxLastN)`, reverse, deliver, then register

### EPHEMERAL

**Use case:** MCP tool calls, request-scoped exchanges, any short-lived one-to-one stream.

**Characteristics:**
- Short TTL (configurable, default 5 minutes)
- One publisher, one or two subscribers maximum
- Explicitly closed by the publisher when the exchange completes
- TTL acts as the safety net if explicit close never arrives

### CHANNEL

**Use case:** Per-user notifications, per-tenant feeds, point-to-point delivery. Potentially
thousands of distinct stream keys with few subscribers each.

**Characteristics:**
- Medium TTL (configurable, default 24 hours)
- Few subscribers per stream key
- Long-lived but not permanent
- Replay supported: `FROM_LAST_EVENT_ID` or `LAST_N`

### BROADCAST

**Use case:** System announcements, global notifications. Few stream keys, many subscribers.

**Characteristics:**
- Long TTL or no expiry (configurable)
- Many subscribers per stream key
- Replay supported: `FROM_LAST_EVENT_ID` or `LAST_N`

---

## Redis Connection Model

Two fixed connections per node. Count never grows regardless of stream count or subscriber
count.

```
Connection 1 — Pub/Sub listener
    PSUBSCRIBE odyssey:notify:*
    One permanent virtual thread. Handles wake-ups for ALL stream types
    (ephemeral, channel, and broadcast). No stream key enumeration required.

Connection 2 — On-demand reads/writes (shared)
    Used for:
      - XADD (publish)
      - XREAD (non-blocking drain on wake-up from Pub/Sub)
      - XRANGE / XREVRANGE (replay on reconnect)
      - PUBLISH (Pub/Sub notification after XADD)
    Lettuce is thread-safe — multiple virtual threads share this connection safely.
    Commands are multiplexed internally by Lettuce. All commands are non-blocking.
```

Total: **2 connections per node**, fixed.

---

## Keep-Alive / Heartbeat

Every subscriber — regardless of stream type — handles its own keep-alive via the writer
thread. There is no separate heartbeat mechanism.

The writer thread calls `queue.poll(keepAliveInterval, MILLISECONDS)`. On timeout (no
events), it sends `SseEmitter.event().comment("keep-alive")` and polls again.

The reader thread also uses `keepAliveInterval` as its `tryAcquire` timeout. On timeout
with no nudge, it does a no-op `XREAD` cycle — this acts as a safety net in case a Pub/Sub
notification was missed.

`keepAliveInterval` is a single config value. Default: 30 seconds.

---

## Resume / Replay

Resume mode is implicit in which method the caller uses:

- `subscribe()` → live only, no history
- `resumeAfter(lastEventId)` → replay strictly after the given cursor, then go live.
  Implemented via `XRANGE <key> <lastEventId> +`, deliver, then register with `TopicFanout`.
- `replayLast(count)` → replay last N events (capped), then go live.
  Implemented via `XREVRANGE <key> + - COUNT min(n, maxLastN)` — returns newest-first,
  reverse before delivering. Cap enforced server-side via `odyssey.redis.max-last-n`.

No `ResumeMode` enum needed — the API is the contract.

---

## Public API

### OdysseyStream

```java
public interface OdysseyStream {

    /** Subscribe to live events from now. Uses the registry's default SSE timeout. */
    SseEmitter subscribe();
    SseEmitter subscribe(Duration timeout);

    /** Resume after a known event ID. Replays missed events, then goes live. */
    SseEmitter resumeAfter(String lastEventId);
    SseEmitter resumeAfter(String lastEventId, Duration timeout);

    /** Replay the last N events (capped at odyssey.redis.max-last-n), then go live. */
    SseEmitter replayLast(int count);
    SseEmitter replayLast(int count, Duration timeout);

    /** Publish to this stream. Returns the Redis Stream entry ID. */
    String publish(String eventType, String payload);

    /** Gracefully close the stream. Existing subscribers are drained then disconnected. */
    void close();

    /** Immediately delete the stream. Existing subscribers are disconnected. */
    void delete();

    String getStreamKey();
}
```

### OdysseyStreamRegistry

```java
public interface OdysseyStreamRegistry {
    OdysseyStream ephemeral();               // auto-generated key (UUID)
    OdysseyStream channel(String name);      // odyssey:channel:<name>
    OdysseyStream broadcast(String name);    // odyssey:broadcast:<name>
}
```

---

## Domain Model

### OdysseyEvent

Immutable. Builder pattern.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Redis Stream entry ID. Opaque to callers. Used as SSE `id:` field. |
| `streamKey` | `String` | The full Redis key this event belongs to. |
| `eventType` | `String` | SSE `event:` field. |
| `payload` | `String` | SSE `data:` field. Typically JSON. |
| `timestamp` | `Instant` | Wall clock time at publish. |
| `metadata` | `Map<String,String>` | Optional caller-supplied metadata. Stored as extra XADD fields. |

A single `POISON` sentinel constant lives in `odyssey-redis` (e.g., on `SubscriberOutbox`).
It signals the writer thread to drain remaining events and exit. Never exposed to callers.

---

## Configuration

```yaml
spring:
  threads:
    virtual:
      enabled: true        # required

odyssey:
  keep-alive-interval: 30s # poll timeout AND keep-alive interval
  sse-timeout: 0            # SseEmitter timeout (0 = no timeout)

  redis:
    stream-prefix: "odyssey:"
    max-len: 100000          # MAXLEN ~ trimming on all streams
    max-last-n: 500          # hard cap for replayLast()

    ttl:
      ephemeral: 5m          # short — tool calls complete quickly
      channel: 1h            # medium — survives reconnects
      broadcast: 24h         # long — system announcements
```

---

## Internal Components

### SubscriberOutbox

Per-subscriber coordination object. Owns the two virtual threads and the `BlockingQueue`
between them.

```java
class SubscriberOutbox {
    Semaphore nudge = new Semaphore(0);
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    String lastReadId;                      // only touched by reader thread
    Thread readerThread;                    // virtual
    Thread writerThread;                    // virtual

    void start();                           // launch both virtual threads
    void nudge();                           // semaphore.release() — called by Pub/Sub listener
    void closeGracefully();                 // interrupt reader, poison queue
    void closeImmediately();                // interrupt both threads
}
```

**Reader thread loop:**
1. `nudge.tryAcquire(keepAliveInterval, MILLISECONDS)`
2. `nudge.drainPermits()` — coalesce any piled-up nudges
3. `XREAD` from `lastReadId` (non-blocking, shared Lettuce connection)
4. Offer events to `queue`, update `lastReadId`
5. Repeat

**Writer thread loop:**
1. `queue.poll(keepAliveInterval, MILLISECONDS)`
2. If `null` → send keep-alive comment to `SseEmitter`, repeat
3. If `POISON` → close `SseEmitter`, exit
4. Otherwise → send event to `SseEmitter`, repeat

**Graceful shutdown (`stream.close()`):**
1. Interrupt reader thread → exits on `InterruptedException`
2. Offer `POISON` to queue → writer drains remaining events, exits

**Immediate shutdown (`stream.delete()` or client disconnect):**
1. Interrupt both threads → both exit on `InterruptedException`

### TopicFanout

Per-stream-key fan-out to local `SubscriberOutbox` instances.

```java
class TopicFanout {
    List<SubscriberOutbox> subscribers;

    void addSubscriber(SubscriberOutbox outbox);
    void removeSubscriber(SubscriberOutbox outbox);
    void nudgeAll();                         // semaphore.release() on each outbox
    void shutdown();                         // closeGracefully() on all outboxes
    boolean hasSubscribers();
}
```

### PubSubNotificationListener

Single virtual thread per node. `PSUBSCRIBE odyssey:notify:*`. On notification:
1. Extract stream key from channel name
2. Look up `TopicFanout` for that stream key
3. If found and has subscribers: call `topicFanout.nudgeAll()`
4. If not found: ignore (no local subscribers)

The listener does zero Redis I/O — just a map lookup and semaphore releases.

---

## Example Usage

### MCP Tool Call (EPHEMERAL)

```java
@PostMapping(value = "/mcp", produces = TEXT_EVENT_STREAM_VALUE)
public SseEmitter handleToolCall(@RequestBody String request,
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {

    OdysseyStream stream = registry.ephemeral();

    if (lastEventId != null) {
        return stream.resumeAfter(lastEventId);
    }

    executorService.submit(() -> {
        try {
            stream.publish("progress", "{\"status\":\"running\"}");
            String result = tool.execute(request);
            stream.publish("result", result);
        } finally {
            stream.close();
        }
    });

    return stream.subscribe();
}
```

### User Notifications (CHANNEL)

```java
@GetMapping(value = "/events/me", produces = TEXT_EVENT_STREAM_VALUE)
public SseEmitter userEvents(
        @AuthenticationPrincipal User user,
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {

    OdysseyStream stream = registry.channel("user:" + user.getId());
    return lastEventId != null
        ? stream.resumeAfter(lastEventId)
        : stream.subscribe();
}

// elsewhere — publish a notification
registry.channel("user:" + userId).publish("notification.changed", toJson(summary));
```

### System Announcements (BROADCAST)

```java
@GetMapping(value = "/events/announcements", produces = TEXT_EVENT_STREAM_VALUE)
public SseEmitter announcements(
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {

    OdysseyStream stream = registry.broadcast("announcements");
    return lastEventId != null
        ? stream.resumeAfter(lastEventId)
        : stream.replayLast(10);
}

// elsewhere — publish an announcement
registry.broadcast("announcements").publish("maintenance", toJson(notice));
```

---

## Definition of "done" for a spec

A spec is done when ALL of the following are true:

- [ ] The feature described in the spec is implemented
- [ ] All existing tests pass (`./mvnw verify`)
- [ ] New tests exist for the new behavior (unless the spec says otherwise)
- [ ] Spotless passes (`./mvnw spotless:check`)
- [ ] No debug code left in
- [ ] progress.txt is updated with verification results

---

## Constraints and guardrails

- Never introduce reactive/WebFlux types
- Never add additional Redis connections beyond the 2-connection model
- Never change the public API interfaces (`OdysseyStream`, `OdysseyStreamRegistry`) without a spec that explicitly calls for it
- Never commit secrets or credentials
- Never use `@SuppressWarnings` annotations — fix the underlying issue instead
