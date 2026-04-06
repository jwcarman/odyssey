# Simplify notifier SPI, rename engine classes, extract StreamEventHandler

## What to build

Three focused cleanups to the engine layer:

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
  - This is THE subscriber. It owns the reader thread, writer thread, and `BlockingQueue`.
  - The reader thread calls `eventLog.readAfter()` — not Redis directly.
  - The writer thread calls a `StreamEventHandler` — not the `SseEmitter` directly.

- `TopicFanout` → `StreamSubscriberGroup`
  - A group of `StreamSubscriber` instances for a single stream key.
  - `nudgeAll()`, `addSubscriber()`, `removeSubscriber()`, `shutdown()`.

- Remove the `readFunction` indirection if it still exists — `StreamSubscriber`
  should take the `OdysseyEventLog` directly and call `readAfter(streamKey, lastReadId)`
  itself.

### 3. Extract StreamEventHandler

Remove the `SseEmitter` reference from `StreamSubscriber` entirely. Instead, the
subscriber delegates to a `StreamEventHandler` interface:

```java
public interface StreamEventHandler {
    void onEvent(OdysseyEvent event);
    void onKeepAlive();
    void onComplete();
    void onError(Exception e);
}
```

`StreamSubscriber`'s writer thread becomes:
```
loop:
    event = queue.poll(keepAliveInterval, MILLISECONDS)
    if event == POISON → handler.onComplete(), exit
    if event == null → handler.onKeepAlive(), continue
    else → handler.onEvent(event), continue
catch exception → handler.onError(e), cleanup
```

The subscriber manages threading and queue coordination only. What happens with the
events is the handler's concern.

**`SseStreamEventHandler` implements `StreamEventHandler`:**
- Lives in `odyssey-core` (since `SseEmitter` is a Spring type and core already
  depends on `spring-web`)
- `onEvent(event)`: `emitter.send(SseEmitter.event().id(event.id()).name(event.eventType()).data(event.payload()))`
- `onKeepAlive()`: `emitter.send(SseEmitter.event().comment("keep-alive"))`
- `onComplete()`: `emitter.complete()`
- `onError(e)`: `emitter.completeWithError(e)`
- Registers `emitter.onCompletion`, `emitter.onTimeout`, `emitter.onError` callbacks
  that route to a cleanup callback provided at construction time
- All `SseEmitter` error handling lives here — `IOException` on `send()` triggers cleanup
- Cleanup is idempotent (safe to call multiple times)

This separation also opens the door to non-SSE consumers (WebSocket, logging, testing)
without changing the subscriber.

## Acceptance criteria

- [ ] `OdysseyStreamNotifier.subscribe` no longer takes a `pattern` parameter
- [ ] All callers updated to use the new signature
- [ ] `SubscriberOutbox` renamed to `StreamSubscriber`
- [ ] `TopicFanout` renamed to `StreamSubscriberGroup`
- [ ] `readFunction` indirection removed — subscriber takes `OdysseyEventLog` directly
- [ ] `StreamEventHandler` interface exists with `onEvent`, `onKeepAlive`, `onComplete`,
      `onError` methods
- [ ] `SseStreamEventHandler` implements `StreamEventHandler` with all `SseEmitter`
      interaction and error handling
- [ ] `StreamSubscriber` has no reference to `SseEmitter` — only to `StreamEventHandler`
- [ ] Cleanup is idempotent and handles all shutdown paths (client disconnect, timeout,
      error, explicit close, delete)
- [ ] Engine still receives notifications and delivers events correctly
- [ ] Unit tests cover `SseStreamEventHandler` error handling paths
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- This is a refactor — behavior should not change from the user's perspective.
- The `SseStreamEventHandler` needs a cleanup callback (e.g., `Runnable`) passed at
  construction time so it can trigger unregistration from the `StreamSubscriberGroup`
  when the emitter completes/errors. The subscriber or the stream creates the handler
  and passes the cleanup logic.
- Tests should be updated for new class/package names.
- Consider: should `StreamEventHandler` live in `org.jwcarman.odyssey.core` (public API)
  or `org.jwcarman.odyssey.engine` (internal)? If we want non-SSE consumers to implement
  it, it should be public API in `core`.
