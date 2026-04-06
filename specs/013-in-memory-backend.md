# Implement in-memory backend

## What to build

Create in-memory implementations of both SPIs in `odyssey-core` under
`org.jwcarman.odyssey.memory`. These serve as the zero-config fallback when no external
backend is on the classpath.

**`InMemoryOdysseyEventLog`:**
- `ConcurrentHashMap<String, BoundedEventList>` — one bounded list per stream key
- `append`: generate a unique event ID (monotonic counter or timestamp-sequence), add to
  the list, evict from the head if the list exceeds `maxLen`
- `readAfter`: return events after the given ID as a `Stream`
- `readLast`: return the last N events in chronological order as a `Stream`
- `delete`: remove the stream key from the map
- Event IDs must be comparable/ordered — use a format like `<counter>-0` to mirror Redis
  Stream ID semantics so existing cursor comparison logic works

**`InMemoryOdysseyStreamNotifier`:**
- Maintain a list of registered `NotificationHandler`s with their patterns
- `notify`: iterate handlers, call those whose pattern matches the stream key
- `subscribe`: register the handler
- Pattern matching: support `*` glob suffix (e.g., `odyssey:notify:*` matches any stream
  key prefixed with `odyssey:notify:`)
- Calls happen synchronously on the publishing thread — no network, no serialization

**Bounded list behavior:**
- Configurable max size (from `OdysseyProperties.redis.maxLen` or a new
  `OdysseyProperties.memory.maxLen`)
- When an event is appended and the list exceeds max size, evict the oldest event
- Thread-safe for concurrent append and read

## Acceptance criteria

- [ ] `InMemoryOdysseyEventLog` implements `OdysseyEventLog`
- [ ] `InMemoryOdysseyStreamNotifier` implements `OdysseyStreamNotifier`
- [ ] Append respects max size — oldest events evicted when full
- [ ] `readAfter` returns only events after the given ID
- [ ] `readLast` returns the last N events in order
- [ ] Generated event IDs are monotonically increasing and comparable
- [ ] Notifier calls matching handlers synchronously
- [ ] Unit tests cover: append + read, eviction, readAfter with cursor, readLast,
      notification dispatch, pattern matching
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- For the bounded list, consider `LinkedList` with a size check on append, or a
  circular buffer. Keep it simple.
- The in-memory notifier's synchronous dispatch means `notify()` blocks until all
  handlers return. This is fine for single-node — the handlers just release semaphores.
