# Support nullable eventType and publish overloads

## What to build

MCP Streamable HTTP sends SSE events with only `id:` and `data:` — no `event:` field.
Odyssey currently requires `eventType` on every publish call. Make it optional.

### 1. Allow null eventType in SseStreamEventHandler

In `SseStreamEventHandler.onEvent()`, only call `.name()` when `eventType` is non-null:

**Before:**
```java
send(SseEmitter.event().id(event.id()).name(event.eventType()).data(event.payload()));
```

**After:**
```java
SseEmitter.SseEventBuilder builder = SseEmitter.event().id(event.id()).data(event.payload());
if (event.eventType() != null) {
    builder.name(event.eventType());
}
send(builder);
```

### 2. Add publish overloads without eventType

Add convenience methods to `OdysseyStream` for publishing without an event type:

```java
String publishRaw(String payload);                    // no eventType
String publishRaw(String eventType, String payload);  // existing

String publishJson(Object payload);                   // no eventType
String publishJson(String eventType, Object payload); // existing
```

The no-eventType overloads create an `OdysseyEvent` with `eventType = null`.

### 3. Allow null eventType in OdysseyEvent

The `OdysseyEvent` record should accept `null` for `eventType`. Update the builder
to not require it. The `eventType()` accessor returns `null` when unset.

### 4. Support priming events (optional but recommended)

MCP requires the server to send an initial empty-data event with an ID when a client
subscribes, so the client has a `Last-Event-ID` for reconnection before any real
events arrive.

Add a `subscribe` overload that sends a priming event:

```java
SseEmitter subscribe(boolean prime);
SseEmitter subscribe(Duration timeout, boolean prime);
```

When `prime` is true, immediately after creating the subscriber, publish an empty
event (`publishRaw("")`) so the client gets an ID. This goes through the normal
event log → notifier → reader → writer pipeline.

Alternatively, a simpler approach: add a `prime()` method on `OdysseyStream` that
publishes an empty event and returns the event ID:

```java
String prime();  // publishes empty event, returns event ID
```

The caller decides when to prime:
```java
OdysseyStream stream = registry.ephemeral();
stream.prime();
return stream.subscribe();
```

Choose whichever approach is cleaner. The key requirement is that the priming event
goes through the event log so it gets an ID that works with `resumeAfter()`.

## Acceptance criteria

- [ ] `eventType` is nullable in `OdysseyEvent`
- [ ] `SseStreamEventHandler` skips `.name()` when `eventType` is null
- [ ] `publishRaw(String payload)` overload exists (no eventType)
- [ ] `publishJson(Object payload)` overload exists (no eventType)
- [ ] Existing `publishRaw(String eventType, String payload)` still works
- [ ] SSE output has no `event:` field when eventType is null
- [ ] Priming support available (either `prime()` method or subscribe option)
- [ ] Unit tests for null eventType path
- [ ] Unit tests for new publish overloads
- [ ] Unit tests for priming
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- The SSE spec says the `event:` field is optional. When omitted, the browser's
  `EventSource` fires the `onmessage` handler instead of a named event listener.
  This is standard SSE behavior.
- Backwards compatible — existing callers that pass eventType are unaffected.
- For priming: `publishRaw("")` works but creates a real event in the log with
  empty payload. Consider whether a dedicated event type like `__prime__` would
  be cleaner for filtering, or if empty payload is fine.
