# Extract send helper in SseStreamEventHandler

## What to build

The `emitter.send()` + try/catch `IOException` → cleanup pattern is repeated in
`SseStreamEventHandler`. Extract a private helper method that all send call sites use.

**Before:**
```java
try {
    emitter.send(SseEmitter.event().comment("keep-alive"));
} catch (IOException e) {
    doCleanup();
}
```

**After:**
```java
private void send(SseEmitter.SseEventBuilder event) {
    try {
        emitter.send(event);
    } catch (IOException e) {
        doCleanup();
    }
}
```

All `emitter.send()` calls in the handler should go through this helper:
- `onKeepAlive`: `send(SseEmitter.event().comment("keep-alive"))`
- `onEvent`: `send(SseEmitter.event().id(...).name(...).data(...))`

## Acceptance criteria

- [ ] `SseStreamEventHandler` has a private `send` helper
- [ ] All `emitter.send()` calls use the helper
- [ ] No direct `emitter.send()` calls remain outside the helper
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Small refactor. Behavior does not change.
