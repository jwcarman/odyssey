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

### 4. Priming events (no dedicated API needed)

MCP requires the server to send an initial event with an ID so the client has a
`Last-Event-ID` for reconnection. This does NOT need a dedicated API — callers
can simply do `stream.publishRaw("")` which creates an event with an ID and empty
data. The client gets the ID and ignores the empty payload.

## Acceptance criteria

- [ ] `eventType` is nullable in `OdysseyEvent`
- [ ] `SseStreamEventHandler` skips `.name()` when `eventType` is null
- [ ] `publishRaw(String payload)` overload exists (no eventType)
- [ ] `publishJson(Object payload)` overload exists (no eventType)
- [ ] Existing `publishRaw(String eventType, String payload)` still works
- [ ] SSE output has no `event:` field when eventType is null
- [ ] Unit tests for null eventType path
- [ ] Unit tests for new publish overloads
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- The SSE spec says the `event:` field is optional. When omitted, the browser's
  `EventSource` fires the `onmessage` handler instead of a named event listener.
  This is standard SSE behavior.
- Backwards compatible — existing callers that pass eventType are unaffected.
- For MCP priming: callers use `stream.publishRaw("")` — no dedicated API needed.
