# Javadoc the public APIs and SPIs

## What to build

Add comprehensive Javadoc to all public interfaces, classes, and methods that
consumers and SPI implementers interact with. This is a library being published to
Maven Central — the Javadoc JAR must be useful.

### Public API (`org.jwcarman.odyssey.core`)

**`OdysseyStream`** — document each method:
- `subscribe()` / `subscribe(Duration)` — what it returns, when threads start
- `resumeAfter(lastEventId)` — SSE reconnect semantics, what happens if the ID is invalid
- `replayLast(count)` — capping behavior, ordering guarantee
- `publishRaw(eventType, payload)` — what the return value is (event ID)
- `publishJson(eventType, payload)` — serialization behavior, ObjectMapper usage
- `close()` vs `delete()` — graceful drain vs immediate stop
- `getStreamKey()` — format, usage for reconnect

**`OdysseyStreamRegistry`** — document each method:
- `ephemeral()` — auto-generated key, typical use case (MCP, request-scoped)
- `channel(name)` — cached by name, per-user/per-entity
- `broadcast(name)` — cached by name, system-wide
- `stream(streamKey)` — lookup by full key, reconnect use case

**`OdysseyEvent`** — document the record and its builder:
- Each field: `id`, `streamKey`, `eventType`, `payload`, `timestamp`, `metadata`
- Builder usage example
- Immutability guarantees (defensive copy of metadata)

**`StreamEventHandler`** — document the interface for non-SSE consumers:
- `onEvent` — called for each event
- `onKeepAlive` — called on poll timeout
- `onComplete` — called on graceful stream close
- `onError` — called on errors

### SPI (`org.jwcarman.odyssey.spi`)

**`OdysseyEventLog`** — document for implementers:
- Class-level: purpose, relationship to `AbstractOdysseyEventLog`
- `ephemeralKey()` / `channelKey()` / `broadcastKey()` — key generation contract
- `append()` — what it stores, what it returns, trimming/TTL expectations
- `readAfter()` — cursor semantics, ordering guarantee, `Stream` laziness
- `readLast()` — ordering (chronological), capping
- `delete()` — what gets removed

**`AbstractOdysseyEventLog`** — document for implementers:
- When to extend it (always, unless you have a very good reason)
- Constructor: prefix parameters
- `generateEventId()` — UUID v7, cluster-safe, time-ordered

**`OdysseyStreamNotifier`** — document for implementers:
- `notify()` — fire-and-forget semantics, what the eventId is for
- `subscribe()` — single handler per node, lifecycle expectations
- Thread management responsibility

**`NotificationHandler`** — document the functional interface:
- `onNotification(streamKey, eventId)` — when it's called, thread context

### Auto-configuration (`org.jwcarman.odyssey.autoconfigure`)

**`OdysseyProperties`** — document each property with defaults and examples

**`OdysseyAutoConfiguration`** — document the fallback behavior and bean creation

## Acceptance criteria

- [ ] All public interfaces have class-level Javadoc
- [ ] All public methods have method-level Javadoc
- [ ] All SPI interfaces have implementer-focused documentation
- [ ] `AbstractOdysseyEventLog` has usage guidance for implementers
- [ ] `OdysseyProperties` documents all properties
- [ ] No `@param` or `@return` tags are empty placeholders
- [ ] Javadoc builds without warnings: `./mvnw javadoc:javadoc -pl odyssey-core`
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Focus on the `odyssey-core` module — that's what consumers and implementers see.
  Backend modules are implementation details; their Javadoc is less critical.
- Use `{@code ...}` for inline code references.
- Use `{@link ...}` for cross-references to other classes/methods.
- Include short code examples in class-level Javadoc where helpful (e.g.,
  `OdysseyStreamRegistry` usage).
- Do not add Javadoc to private/package-private methods or to test code.
