# Define public API interfaces in odyssey-core

## What to build

Create the two public interfaces and the event record that form OdySSEy's public API in the
`odyssey-core` module. These have no implementation — just the contracts.

**`OdysseyStream` interface:**
```java
public interface OdysseyStream {
    SseEmitter subscribe();
    SseEmitter subscribe(Duration timeout);
    SseEmitter resumeAfter(String lastEventId);
    SseEmitter resumeAfter(String lastEventId, Duration timeout);
    SseEmitter replayLast(int count);
    SseEmitter replayLast(int count, Duration timeout);
    String publish(String eventType, String payload);
    void close();
    void delete();
    String getStreamKey();
}
```

**`OdysseyStreamRegistry` interface:**
```java
public interface OdysseyStreamRegistry {
    OdysseyStream ephemeral();
    OdysseyStream channel(String name);
    OdysseyStream broadcast(String name);
}
```

**`OdysseyEvent` record with builder:**

| Field | Type |
|---|---|
| `id` | `String` |
| `streamKey` | `String` |
| `eventType` | `String` |
| `payload` | `String` |
| `timestamp` | `Instant` |
| `metadata` | `Map<String,String>` |

`metadata` should default to an empty unmodifiable map. The record should be immutable —
the `Map` must be copied defensively in the constructor. Provide a builder class for
ergonomic construction.

## Acceptance criteria

- [ ] `OdysseyStream` interface exists at `org.jwcarman.odyssey.core.OdysseyStream`
- [ ] `OdysseyStreamRegistry` interface exists at `org.jwcarman.odyssey.core.OdysseyStreamRegistry`
- [ ] `OdysseyEvent` record exists at `org.jwcarman.odyssey.core.OdysseyEvent` with all 6 fields
- [ ] `OdysseyEvent.builder()` returns a builder that constructs an `OdysseyEvent`
- [ ] `OdysseyEvent.metadata()` returns an unmodifiable map
- [ ] Unit tests verify builder construction and immutability (defensive copy of metadata)
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- `odyssey-core` needs `spring-web` as a `provided` or `compileOnly` dependency for
  `SseEmitter`. It should not pull Spring in transitively.
- Keep `odyssey-core` as thin as possible — interfaces and the event record only.
