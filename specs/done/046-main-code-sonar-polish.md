# Main-code Sonar polish: fix reliability bug and major code smells

## What to build

Fix all SonarCloud issues currently flagged against `odyssey-core/src/main`, `odyssey-example/src/main`, and the `autoconfigure` package. The headline item is a `java:S3077` reliability bug in `SseJournalAdapter`; the rest are code smells that together make the quality gate noisy.

### Issues in scope

| Rule | Severity | File | Action |
|---|---|---|---|
| `java:S3077` | MINOR (BUG) | `SseJournalAdapter.java:44` | Refactor `private volatile BlockingSubscription source` to `private final BlockingSubscription source` by creating the subscription in a static factory, then passing it into the constructor. No volatile, no null check in `close()`. |
| `java:S1141` | MAJOR | `SseJournalAdapter.java:198` | Extract the nested try inside `trySendTerminal` into a separate `sendTerminalFrame` method. |
| `java:S1172` | MAJOR | `DefaultOdyssey.java:150` | Remove the unused `Class<T> type` parameter from `createPublisher`. It's unused — the `Class<T>` is already captured via the `Journal<StoredEvent>` creation path. |
| `java:S7467` | MINOR | `SseJournalAdapter.java:65`, `DefaultOdyssey.java:166`, `SseJournalAdapter.java:151` | Replace unused catch-variable names with the Java 22+ unnamed pattern `_`. |
| `java:S2629` | MAJOR (×4) | `DefaultOdysseyPublisher.java:56,62,68,74` | Guard `log.debug(...)` calls with `log.isDebugEnabled()` OR (better) confirm the SLF4J placeholders don't trigger eager evaluation; Sonar may be flagging a call to `journal.key()` as eager. Refactor so no argument is computed unless the level is enabled. |
| `java:S1186` | CRITICAL | `OdysseyAutoConfiguration.java:36` | The explicit empty constructor exists to satisfy Spring Boot's auto-configuration processor. Add a comment explaining why it's empty (or remove it — if the default constructor works, delete the explicit one). |

### Detailed fixes

#### S3077 — `SseJournalAdapter` refactor to final `source`

Replace the current pattern:

```java
private volatile BlockingSubscription<JournalEntry<StoredEvent>> source;

void start() {
  BlockingSubscription<JournalEntry<StoredEvent>> sub;
  try {
    sub = sourceSupplier.get();
  } catch (JournalExpiredException e) {
    // inline terminal-expired handling
    ...
    return;
  }
  this.source = sub;
  // wire callbacks, spawn thread
}
```

With a static factory method that handles the `JournalExpiredException` before constructing the adapter:

```java
static <T> void launch(
    Supplier<BlockingSubscription<JournalEntry<StoredEvent>>> sourceSupplier,
    SseEmitter emitter,
    String streamKey,
    DefaultSubscriberConfig<T> config,
    ObjectMapper objectMapper,
    Class<T> type) {
  BlockingSubscription<JournalEntry<StoredEvent>> sub;
  try {
    sub = sourceSupplier.get();
  } catch (JournalExpiredException _) {
    emitExpiredBeforeAdapterExists(emitter, config, streamKey);
    return;
  }
  new SseJournalAdapter<>(sub, emitter, streamKey, config, objectMapper, type).begin();
}

private SseJournalAdapter(
    BlockingSubscription<JournalEntry<StoredEvent>> source,
    SseEmitter emitter,
    String streamKey,
    DefaultSubscriberConfig<T> config,
    ObjectMapper objectMapper,
    Class<T> type) {
  this.source = source;
  this.emitter = emitter;
  this.streamKey = streamKey;
  this.config = config;
  this.objectMapper = objectMapper;
  this.type = type;
}

private void begin() {
  emitter.onCompletion(this::close);
  emitter.onError(_ -> close());
  emitter.onTimeout(this::close);
  Thread.ofVirtual().name("odyssey-writer-" + streamKey).start(this::writerLoop);
}

void close() {
  if (closed.compareAndSet(false, true)) {
    source.cancel();   // source is final, always non-null
  }
}
```

- `source` is final — no volatile, no null check in `close()`.
- `emitExpiredBeforeAdapterExists` is a package-private static helper that fires `mapper.terminal(new TerminalState.Expired())`, runs `config.onExpired()`, and calls `emitter.complete()`.
- `DefaultOdyssey.startAdapter` calls `SseJournalAdapter.launch(...)` and returns the emitter.

The static factory exists solely so that exception handling during subscription creation runs *before* the constructor — preserving the "constructor either fully succeeds or throws" invariant so `source` can be final.

#### S1141 — extract nested try

Current `trySendTerminal` has a try-inside-try:

```java
private boolean trySendTerminal(TerminalState state) {
  try {
    Optional<SseEmitter.SseEventBuilder> event = config.mapper().terminal(state);
    if (event.isEmpty()) return false;
    try {
      emitter.send(event.get());
      return true;
    } catch (IOException e) {
      log.debug(...);
      return false;
    }
  } catch (Exception e) {
    log.debug(...);
    return false;
  }
}
```

Extract the inner send into `sendTerminalFrame(SseEmitter.SseEventBuilder frame) throws IOException`, so `trySendTerminal` has a single try block:

```java
private boolean trySendTerminal(TerminalState state) {
  try {
    Optional<SseEmitter.SseEventBuilder> frame = config.mapper().terminal(state);
    if (frame.isEmpty()) return false;
    return sendTerminalFrame(frame.get());
  } catch (Exception _) {
    log.debug("[{}] Error building terminal event", streamKey);
    return false;
  }
}

private boolean sendTerminalFrame(SseEmitter.SseEventBuilder frame) {
  try {
    emitter.send(frame);
    return true;
  } catch (IOException _) {
    log.debug("[{}] Failed to send terminal event", streamKey);
    return false;
  }
}
```

#### S1172 — unused `Class<T> type` parameter

`DefaultOdyssey.createPublisher` currently takes a `Class<T> type` that isn't used. Remove it and pull the type from the call site into whatever downstream needs it. Verify all call sites compile.

#### S7467 — unnamed catch patterns

Replace unused exception variables with `_` (Java 22+):

- `SseJournalAdapter.java:65` — the `emitter.onError(e -> close())` lambda doesn't use `e`, change to `emitter.onError(_ -> close())`. (Note: lambda parameter, not catch variable, but Sonar still flags it.)
- `DefaultOdyssey.java:166` — `catch (JournalAlreadyExistsException e)` → `catch (JournalAlreadyExistsException _)`.
- `SseJournalAdapter.java:151` — any catch block in `trySendTerminal` / `sendTerminalFrame` that doesn't use the exception variable.

#### S2629 — `DefaultOdysseyPublisher` logging eagerness

SLF4J placeholders are lazy, but Sonar flags calls like `log.debug("[{}] Published event id={} type={}", journal.key(), id, eventType)` because `journal.key()` is evaluated regardless of the log level. Fix by caching `journal.key()` into a local `key` field in the constructor so the log call uses a pre-computed value:

```java
class DefaultOdysseyPublisher<T> implements OdysseyPublisher<T> {
  private final Journal<StoredEvent> journal;
  private final String key;  // NEW: cached at construction time
  // ...

  DefaultOdysseyPublisher(Journal<StoredEvent> journal, ...) {
    this.journal = journal;
    this.key = journal.key();
    // ...
  }

  @Override
  public String publish(String eventType, T data) {
    String json = objectMapper.writeValueAsString(data);
    StoredEvent event = new StoredEvent(eventType, json, Map.of());
    String id = journal.append(event, entryTtl);
    log.debug("[{}] Published event id={} type={}", key, id, eventType);
    return id;
  }

  @Override
  public void close() {
    log.debug("[{}] Completing journal with retention={}", key, retentionTtl);
    journal.complete(retentionTtl);
  }

  // ... same pattern for close(Duration) and delete()

  @Override
  public String key() {
    return key;
  }
}
```

This also has the nice side-effect of memoizing `key()` so callers don't pay the cost on every invocation.

#### S1186 — empty constructor in `OdysseyAutoConfiguration`

```java
public OdysseyAutoConfiguration() {}
```

Add a javadoc comment explaining that the no-arg constructor exists explicitly for Spring Boot's auto-configuration processor. Or simpler: delete the explicit constructor entirely and rely on the default.

### Out of scope

- Test-file issues (spec 047)
- Example app issues (spec 048)
- Coverage gaps (spec 049)

## Acceptance criteria

- [ ] `SseJournalAdapter.source` is `private final` (no `volatile`)
- [ ] `SseJournalAdapter` has a static `launch(...)` factory that handles `JournalExpiredException` before the constructor runs
- [ ] `SseJournalAdapter.close()` has no null check on `source`
- [ ] `SseJournalAdapter.trySendTerminal` has only one try block; the IOException-catching branch lives in a separate `sendTerminalFrame` helper
- [ ] `DefaultOdyssey.createPublisher` has no unused `Class<T> type` parameter
- [ ] All unused catch-variables / lambda-parameters use `_` (unnamed pattern)
- [ ] `DefaultOdysseyPublisher` caches `journal.key()` as a final field at construction time and uses the cached value in all log calls
- [ ] `OdysseyAutoConfiguration` either has a javadoc-commented empty constructor or no explicit constructor at all
- [ ] `./mvnw clean install` passes
- [ ] All 48 existing odyssey-core tests still pass
- [ ] A new unit test exercises the `JournalExpiredException` path through `SseJournalAdapter.launch(...)` (verify terminal-expired fires and `emitter.complete()` is called without ever spawning a writer thread)
- [ ] SonarCloud quality gate passes: `new_reliability_rating = 1`, zero new bugs, zero S3077 / S1141 / S1172 / S7467 / S2629 / S1186 issues against files in this spec

## Implementation notes

- `SseJournalAdapter` is package-private, so the static `launch` method can also be package-private. `DefaultOdyssey` calls it directly.
- The existing `start()` method should be deleted — replaced entirely by the static `launch` factory and the instance `begin()` method.
- `SseJournalAdapterTest` currently mocks the `sourceSupplier` and calls `adapter.start()`. After the refactor, tests will need to call `SseJournalAdapter.launch(...)` OR use a new package-private constructor that takes a pre-created subscription directly. Pick whichever makes the test shorter; prefer the direct-constructor approach for unit tests since it avoids the supplier indirection and lets us mock the subscription directly.
- Add a test for `launch()` with a supplier that throws `JournalExpiredException` — verify the terminal-expired path fires (mapper called with `TerminalState.Expired()`, `onExpired` callback fires, `emitter.complete()` is called, writer thread is never spawned).
- The `emitExpiredBeforeAdapterExists` helper is a tiny bit of duplicate logic with the in-loop `TerminalState.Expired` handling. It's worth the duplication to keep the static factory self-contained. Alternatively, the static factory could construct the adapter unconditionally and then call a new instance method — but that means the constructor accepts `null` source, which defeats the whole point of making `source` final. Keep the duplication.
