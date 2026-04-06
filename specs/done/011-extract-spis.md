# Extract OdysseyEventLog and OdysseyStreamNotifier SPIs

## What to build

Define the two SPI interfaces in `odyssey-core` under the `org.jwcarman.odyssey.spi`
package. These decouple the engine from any specific storage or notification backend.

**`OdysseyEventLog`:**
```java
public interface OdysseyEventLog {
    String append(String streamKey, OdysseyEvent event);
    Stream<OdysseyEvent> readAfter(String streamKey, String lastId);
    Stream<OdysseyEvent> readLast(String streamKey, int count);
}
```

- `append`: stores an event, returns the assigned event ID
- `readAfter`: returns events strictly after `lastId`, lazily via `Stream`
- `readLast`: returns the last `count` events in chronological order, lazily via `Stream`

**`OdysseyStreamNotifier`:**
```java
public interface OdysseyStreamNotifier {
    void notify(String streamKey, String eventId);
    void subscribe(String pattern, NotificationHandler handler);
}
```

- `notify`: signal that a new event was appended to a stream
- `subscribe`: register a handler for notifications matching a pattern
- Define `NotificationHandler` as a functional interface:
  `void onNotification(String streamKey, String eventId)`

These are interfaces only — no implementations in this spec.

## Acceptance criteria

- [ ] `OdysseyEventLog` interface exists at `org.jwcarman.odyssey.spi.OdysseyEventLog`
- [ ] `OdysseyStreamNotifier` interface exists at `org.jwcarman.odyssey.spi.OdysseyStreamNotifier`
- [ ] `NotificationHandler` functional interface exists at `org.jwcarman.odyssey.spi.NotificationHandler`
- [ ] `readAfter` and `readLast` return `Stream<OdysseyEvent>` (not `List`)
- [ ] No implementations yet — interfaces only
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- These interfaces live in `odyssey-core` so both the engine and backend modules can
  depend on them.
- Keep them minimal — resist adding convenience methods. Implementations should be simple
  to write.
