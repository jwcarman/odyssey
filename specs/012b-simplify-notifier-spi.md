# Simplify notifier SPI and rename engine classes

## What to build

Two focused cleanups to the engine layer:

### 1. Simplify `OdysseyStreamNotifier.subscribe`

Remove the `pattern` parameter — it's an implementation detail.

**Before:**
```java
public interface OdysseyStreamNotifier {
    void notify(String streamKey, String eventId);
    void subscribe(String pattern, NotificationHandler handler);
}
```

**After:**
```java
public interface OdysseyStreamNotifier {
    void notify(String streamKey, String eventId);
    void subscribe(NotificationHandler handler);
}
```

Update all callers. Each notifier implementation owns its own subscription mechanism
internally (Redis: `PSUBSCRIBE`, PostgreSQL: `LISTEN`, etc.). The stream prefix from
`OdysseyProperties` should be passed to the notifier at construction time if needed.

Thread management stays in the implementation — core just calls `subscribe(handler)` and
expects callbacks via `handler.onNotification(streamKey, eventId)`.

### 2. Rename engine classes

- `SubscriberOutbox` → `StreamSubscriber`
  - This is THE subscriber. It owns the reader thread, writer thread, `BlockingQueue`,
    `SseEmitter`, and all error handling/cleanup.
  - The reader thread calls `eventLog.readAfter()` — not Redis directly.
  - All `SseEmitter` error handling (`onCompletion`, `onTimeout`, `onError`, `IOException`
    on `send()`) must be consolidated in this one class. One cleanup path: interrupt reader,
    close emitter, unregister from subscriber group.

- `TopicFanout` → `StreamSubscriberGroup`
  - A group of `StreamSubscriber` instances for a single stream key.
  - `nudgeAll()`, `addSubscriber()`, `removeSubscriber()`, `shutdown()`.

- Remove the `readFunction` indirection if it still exists — `StreamSubscriber`
  should take the `OdysseyEventLog` directly and call `readAfter(streamKey, lastReadId)`
  itself.

### 3. Consolidate SseEmitter error handling

All `SseEmitter` interaction lives in `StreamSubscriber`'s writer thread. Nobody
else touches the emitter. The error handling pattern:

- `emitter.onCompletion(this::cleanup)`
- `emitter.onTimeout(this::cleanup)`
- `emitter.onError(e -> this.cleanup())`
- `emitter.send()` wrapped in try/catch for `IOException` → call `cleanup()`
- `cleanup()`: interrupt reader thread, unregister from `StreamSubscriberGroup`, idempotent
  (safe to call multiple times from different callbacks)

## Acceptance criteria

- [ ] `OdysseyStreamNotifier.subscribe` no longer takes a `pattern` parameter
- [ ] All callers updated to use the new signature
- [ ] `SubscriberOutbox` renamed to `StreamSubscriber`
- [ ] `TopicFanout` renamed to `StreamSubscriberGroup`
- [ ] `readFunction` indirection removed — subscriber takes `OdysseyEventLog` directly
- [ ] All `SseEmitter` error handling consolidated in `StreamSubscriber`
- [ ] Single `cleanup()` method handles all shutdown paths idempotently
- [ ] Engine still receives notifications and delivers events correctly
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- This is a refactor — behavior should not change. Tests need package/class name updates.
- The `cleanup()` method will be called from multiple sources (emitter callbacks, explicit
  close, error paths). Use an `AtomicBoolean` or similar to ensure it runs exactly once.
