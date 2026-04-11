# Redesign Odyssey's public API around customizers, producer/consumer split, and typed events

## What to build

Replace Odyssey's current `OdysseyStream` / `OdysseyStreamRegistry` API with a new surface built around four ideas that only became practical once Substrate 1.0 landed:

1. **Producer/consumer split.** `OdysseyPublisher<T>` owns the journal lifecycle and publishes typed events. `Odyssey.subscribe(...)` / `resume(...)` / `replay(...)` return `SseEmitter` directly — there is no user-visible subscriber type, because the `SseEmitter` *is* the subscription handle.
2. **Customizer-based configuration.** Every configurable method takes a `Consumer<PublisherConfig>` or `Consumer<SubscriberConfig<T>>` instead of returning a fluent builder. The library owns the builder lifecycle; the user mutates a config object inside a lambda; the library returns the real thing atomically. Matches Spring Boot's `RestClientCustomizer` / `HttpSecurityCustomizer` idiom.
3. **Typed domain events, not `OdysseyEvent`.** Users work with their own `T`. `OdysseyEvent` goes away entirely.
4. **Rich terminal-state signaling.** Substrate's `NextResult` distinguishes `Completed` / `Expired` / `Deleted` / `Errored`; Odyssey surfaces each as a distinct SSE event before closing the emitter, via a new `SseEventMapper.terminal(TerminalReason)` hook.

This is a breaking change. Odyssey is pre-1.0 and has no external users. Cut it clean — no deprecation period, no wire-format compatibility with the old `OdysseyEvent` payload.

### The `Odyssey` facade

Single top-level interface, wired as the Spring Boot bean by `OdysseyAutoConfiguration`:

```java
public interface Odyssey {

  // ---- Publisher side (producer) ----

  <T> OdysseyPublisher<T> publisher(String key, Class<T> type);
  <T> OdysseyPublisher<T> publisher(
      String key, Class<T> type, Consumer<PublisherConfig> customizer);

  // Sugared shortcuts that pre-populate PublisherConfig from OdysseyProperties
  <T> OdysseyPublisher<T> ephemeral(Class<T> type);
  <T> OdysseyPublisher<T> ephemeral(
      Class<T> type, Consumer<PublisherConfig> customizer);

  <T> OdysseyPublisher<T> channel(String name, Class<T> type);
  <T> OdysseyPublisher<T> channel(
      String name, Class<T> type, Consumer<PublisherConfig> customizer);

  <T> OdysseyPublisher<T> broadcast(String name, Class<T> type);
  <T> OdysseyPublisher<T> broadcast(
      String name, Class<T> type, Consumer<PublisherConfig> customizer);

  // ---- Subscriber side (consumer) ----
  // Starting position is in the method name, not the config.

  /** Live tail from the current head — only new entries are delivered. */
  <T> SseEmitter subscribe(String key, Class<T> type);
  <T> SseEmitter subscribe(
      String key, Class<T> type, Consumer<SubscriberConfig<T>> customizer);

  /** Resume strictly after a known entry id, then continue tailing. */
  <T> SseEmitter resume(String key, Class<T> type, String lastEventId);
  <T> SseEmitter resume(
      String key, Class<T> type, String lastEventId,
      Consumer<SubscriberConfig<T>> customizer);

  /** Replay the last N retained entries, then continue tailing. */
  <T> SseEmitter replay(String key, Class<T> type, int count);
  <T> SseEmitter replay(
      String key, Class<T> type, int count,
      Consumer<SubscriberConfig<T>> customizer);
}
```

### `OdysseyPublisher<T>`

```java
public interface OdysseyPublisher<T> extends AutoCloseable {

  /** Append an entry with no SSE event type. Returns the Substrate entry id. */
  String publish(T data);

  /** Append an entry with an SSE event type (becomes the `event:` field). */
  String publish(String eventType, T data);

  /**
   * Complete the underlying journal with the retention TTL from {@link PublisherConfig}.
   * After this call, no further {@code publish} calls are accepted; existing entries
   * remain readable for the retention window.
   *
   * <p>Used by try-with-resources.
   */
  @Override
  void close();

  /** Complete with an explicit retention override. */
  void close(Duration retentionTtl);

  /** Explicitly delete the journal. Active subscribers receive {@code NextResult.Deleted}. */
  void delete();

  /** The backend key for this stream (for building SSE endpoint URLs, etc). */
  String key();
}
```

Notes:

- Publisher is immutable after construction. The customizer runs once at `publisher(...)` time; TTLs are frozen into the `DefaultOdysseyPublisher`.
- `close()` calls `journal.complete(cfg.retentionTtl())`. Users who want try-with-resources semantics get the right behavior for free.
- Publisher does **not** track active subscribers. When the publisher calls `journal.delete()`, Substrate notifies every subscriber via `NextResult.Deleted`, and their writer loops terminate their own emitters. No cross-process bookkeeping needed.

### `PublisherConfig`

```java
public interface PublisherConfig {

  /**
   * Inactivity TTL passed to {@code JournalFactory.create(name, type, inactivityTtl)}.
   * Creation-time only — ignored if this publisher falls back to {@code connect()}
   * because the journal already exists.
   */
  PublisherConfig inactivityTtl(Duration ttl);

  /** Default per-entry TTL passed to every {@code journal.append(data, ttl)}. */
  PublisherConfig entryTtl(Duration ttl);

  /** Retention TTL used by {@link OdysseyPublisher#close()} when called with no args. */
  PublisherConfig retentionTtl(Duration ttl);
}
```

Three knobs. Nothing else.

### `SubscriberConfig<T>`

```java
public interface SubscriberConfig<T> {

  /** SSE emitter timeout (maps to the SseEmitter constructor). Zero = no timeout. */
  SubscriberConfig<T> timeout(Duration timeout);

  /** How often to send a keep-alive comment when no entries are available. */
  SubscriberConfig<T> keepAliveInterval(Duration interval);

  /** Per-call mapper override. If unset, uses the default injected by Spring. */
  SubscriberConfig<T> mapper(SseEventMapper<T> mapper);

  /** Called after the terminal SSE event is sent, before the emitter is completed. */
  SubscriberConfig<T> onCompleted(Runnable action);
  SubscriberConfig<T> onExpired(Runnable action);
  SubscriberConfig<T> onDeleted(Runnable action);
  SubscriberConfig<T> onErrored(Consumer<Throwable> action);
}
```

Starting-position methods (`fromId`, `fromLastN`, `fromLiveTail`) are **not** on the config. They are encoded in the top-level `Odyssey.subscribe` / `resume` / `replay` method the caller chose.

### `DeliveredEvent<T>` and `StoredEvent<T>`

```java
// Public — what SseEventMapper receives.
public record DeliveredEvent<T>(
    String id,                       // from JournalEntry.id()
    String streamKey,                 // from JournalEntry.key()
    Instant timestamp,                // from JournalEntry.timestamp()
    String eventType,                 // nullable; becomes the `event:` SSE field
    T data,                           // user's typed payload
    Map<String, String> metadata      // user-supplied, possibly empty
) {}

// Package-private — the actual journal payload.
record StoredEvent<T>(
    String eventType,
    T data,
    Map<String, String> metadata
) {}
```

Users never type `StoredEvent`. The `Journal<StoredEvent<T>>` parameterization is an implementation detail of `DefaultOdyssey` and the adapter.

### `SseEventMapper<T>`

```java
public interface SseEventMapper<T> {

  /** Map a delivered event to an SSE frame. */
  SseEmitter.SseEventBuilder map(DeliveredEvent<T> event);

  /**
   * Called when the subscription terminates. Return a frame to write to the emitter
   * before completing it; return empty to skip the terminal event.
   *
   * <p>The default implementation emits {@code event: odyssey-completed} / {@code
   * odyssey-expired} / {@code odyssey-deleted} / {@code odyssey-errored} SSE events,
   * so browser clients can use {@code addEventListener} to react without parsing
   * comments.
   */
  default Optional<SseEmitter.SseEventBuilder> terminal(TerminalReason reason) {
    return Optional.of(
        SseEmitter.event().name("odyssey-" + reason.name().toLowerCase()).data(""));
  }

  enum TerminalReason { COMPLETED, EXPIRED, DELETED, ERRORED }

  /** Default mapper: uses {@code DeliveredEvent.id} as SSE id, {@code eventType} as name. */
  static <T> SseEventMapper<T> defaultMapper(ObjectMapper objectMapper) { ... }
}
```

The default mapper depends on `ObjectMapper` only for serializing the `data` field to a JSON string in the SSE frame — not for storing it (Substrate's codec handles that).

### `SseJournalAdapter<T>` (package-private)

The only class that imports `org.springframework.web.servlet.mvc.method.annotation.SseEmitter`. It takes a `BlockingSubscription<JournalEntry<StoredEvent<T>>>` and an `SseEmitter`, spawns a virtual-thread writer loop, and pattern-matches on `NextResult` to drive the emitter:

```java
class SseJournalAdapter<T> {

  SseJournalAdapter(
      BlockingSubscription<JournalEntry<StoredEvent<T>>> source,
      SseEmitter emitter,
      String streamKey,
      SubscriberConfig<T> config,
      SseEventMapper<T> mapper) { ... }

  void start() {
    emitter.onCompletion(this::close);
    emitter.onError(e -> close());
    emitter.onTimeout(this::close);
    sendComment("connected");
    Thread.ofVirtual().name("odyssey-writer-" + streamKey).start(this::writerLoop);
  }

  private void writerLoop() {
    try {
      while (source.isActive()) {
        NextResult<JournalEntry<StoredEvent<T>>> result =
            source.next(config.keepAliveInterval());
        switch (result) {
          case NextResult.Value<...> v -> sendEvent(toDelivered(v.value()));
          case NextResult.Timeout<...> t -> sendComment("keep-alive");
          case NextResult.Completed<...> c -> { emitTerminal(COMPLETED); return; }
          case NextResult.Expired<...> e -> { emitTerminal(EXPIRED); return; }
          case NextResult.Deleted<...> d -> { emitTerminal(DELETED); return; }
          case NextResult.Errored<...> err -> { emitTerminalError(err.cause()); return; }
        }
      }
    } catch (IOException clientGone) {
      // Client disconnected mid-write. Just cancel the source and exit — don't
      // try to emit a terminal frame on a broken pipe.
      close();
    } finally {
      emitter.complete();
    }
  }

  void close() { source.cancel(); }
}
```

This is where the "three SSE management wins from Substrate 1.0" actually show up:

1. **Guaranteed drain before `Completed`.** Substrate contract: subscribers see every `Value` before the terminal marker. Publishers can `close()` without worrying about late subscribers losing the tail.
2. **Distinct terminal-state SSE events.** Clients can `addEventListener('odyssey-deleted', ...)` and make informed reconnection decisions.
3. **Clean error classification.** Backend errors arrive as `NextResult.Errored` and get a structured terminal SSE event; `IOException` from `emitter.send()` means the client is gone and we exit quietly without trying to write to a broken pipe.

### Lifecycle

**Publisher side — eager.** `Odyssey.publisher(...)` runs the customizer, calls `createOrConnect` against the backend, wraps the result in a `DefaultOdysseyPublisher`, and returns. One backend round-trip at call time. Errors (bad TTL, backend down) surface here, not at first publish.

**Subscriber side — lazy.** `Odyssey.subscribe(...)` runs the customizer, calls `journalFactory.connect(...)` (no I/O), builds the `SseEmitter`, constructs an `SseJournalAdapter`, calls `adapter.start()` (which spawns the writer thread), and returns the emitter. The first backend round-trip happens inside the writer thread when it calls `journal.subscribe()` / `subscribeAfter(id)` / `subscribeLast(count)`. If that call throws `JournalExpiredException` (journal gone), the writer loop catches it, emits the appropriate terminal SSE event, and completes the emitter — fail fast, no retry.

**No caching.** `Odyssey.publisher(...)` and the sugared shortcuts return a fresh publisher every call. With Substrate's `createOrConnect` fallback, subsequent publishers for the same stream cost one backend round-trip that immediately falls through to `connect`. Users who want memoization wire it as `@Bean` methods in their own Spring config, where the scope and lifecycle are explicit.

### `createOrConnect` — the cluster-safe provisioning dance

```java
// Inside DefaultOdyssey
private <T> Journal<StoredEvent<T>> createOrConnect(
    String key, Class<T> type, Duration inactivityTtl) {
  TypeRef<StoredEvent<T>> typeRef = storedEventTypeRef(type);
  try {
    return journalFactory.create(key, typeRef, inactivityTtl);
  } catch (JournalAlreadyExistsException e) {
    return journalFactory.connect(key, typeRef);
  }
}
```

This is what fixes the cross-instance `JournalAlreadyExistsException` bug that the old `DefaultOdysseyStreamRegistry` would hit when two app instances raced to `channel("user:123")`. It lives in exactly one place — the facade — and every publisher goes through it.

### Spring Boot integration

`OdysseyAutoConfiguration` wires:

- `OdysseyProperties` — same as today (keep-alive interval, SSE timeout, three per-category TTLs)
- `Odyssey` bean (class `DefaultOdyssey`) depending on `JournalFactory`, `ObjectMapper` (for the default mapper), `OdysseyProperties`, and `ObjectProvider<PublisherCustomizer>` + `ObjectProvider<SubscriberCustomizer>` for stacking app-wide customizers

```java
@AutoConfiguration
@EnableConfigurationProperties(OdysseyProperties.class)
@PropertySource("classpath:odyssey-defaults.properties")
public class OdysseyAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Odyssey odyssey(
      JournalFactory journalFactory,
      ObjectMapper objectMapper,
      OdysseyProperties properties,
      ObjectProvider<PublisherCustomizer> publisherCustomizers,
      ObjectProvider<SubscriberCustomizer> subscriberCustomizers) {
    return new DefaultOdyssey(
        journalFactory, objectMapper, properties,
        publisherCustomizers.orderedStream().toList(),
        subscriberCustomizers.orderedStream().toList());
  }
}
```

`PublisherCustomizer` and `SubscriberCustomizer` are marker interfaces extending `Consumer<PublisherConfig>` and (something — see open questions) respectively, so users can drop a `@Bean PublisherCustomizer ...` into their `@Configuration` and have it stack ahead of their per-call customizer.

The three-tier default lookup for publisher TTLs, resolved in order:

1. Hardcoded fallbacks in `DefaultPublisherConfig` (e.g., inactivityTtl = 1h, entryTtl = 1h, retentionTtl = 5m)
2. All `PublisherCustomizer` beans in declared order
3. The caller's per-call `Consumer<PublisherConfig>`

Sugared methods (`channel`, `broadcast`, `ephemeral`) insert their category TTL between steps 1 and 2 — so a user's `@Bean PublisherCustomizer` still gets the last word.

### What goes away

Delete from `odyssey-core`:

- `OdysseyStream` (interface)
- `OdysseyStreamRegistry` (interface)
- `OdysseyEvent` (record)
- `StreamSubscriberBuilder` (interface)
- `SseEventMapper` (old non-generic version — replaced by the new generic one with the terminal hook)
- `DefaultOdysseyStream` (class)
- `DefaultOdysseyStreamRegistry` (class)
- `StreamSubscription` (class — replaced by `SseJournalAdapter<T>`)

Delete `ObjectMapper` from `DefaultOdysseyStream` / `DefaultOdysseyStreamRegistry`'s concerns — serialization now lives entirely in Substrate's codec layer. `ObjectMapper` is only needed by the default `SseEventMapper` for writing JSON into the SSE `data:` field.

Delete `publishJson(...)` overloads — with typed payloads they're redundant. `publisher("x", Map.class).publish(myMap)` covers the dynamic case.

Delete `publishRaw(...)` — replaced by `publisher("x", String.class).publish(text)`.

### Backend starter modules

`odyssey-redis-spring-boot-starter`, `odyssey-postgresql-spring-boot-starter`, `odyssey-nats-spring-boot-starter`, `odyssey-hazelcast-spring-boot-starter`, `odyssey-inmemory-spring-boot-starter` are **unchanged** — they only depend on `odyssey-core` + a Substrate backend module. All the API churn is inside `odyssey-core`.

### Example: SSE controller with the new API

```java
@RestController
@RequiredArgsConstructor
class OrderStreamController {

  private final Odyssey odyssey;

  @PostMapping("/orders")
  public OrderResponse createOrder(@RequestBody CreateOrder cmd) {
    Order order = orderService.create(cmd);
    try (var pub = odyssey.channel("orders:" + order.id(), OrderEvent.class)) {
      pub.publish("order.created", OrderEvent.created(order));
      // ... more events
    }
    return OrderResponse.from(order);
  }

  @GetMapping("/streams/orders/{id}")
  public SseEmitter streamOrder(
      @PathVariable String id,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    String key = "orders:" + id;
    return lastEventId != null
        ? odyssey.resume(key, OrderEvent.class, lastEventId,
            cfg -> cfg.timeout(Duration.ofMinutes(30)))
        : odyssey.subscribe(key, OrderEvent.class,
            cfg -> cfg.timeout(Duration.ofMinutes(30)));
  }
}
```

## Acceptance criteria

### New types (all under `org.jwcarman.odyssey.core`)

- [ ] `Odyssey` interface with 14 methods as specified above
- [ ] `OdysseyPublisher<T>` interface extending `AutoCloseable`
- [ ] `PublisherConfig` interface with 3 TTL setters
- [ ] `SubscriberConfig<T>` interface with 7 setters (timeout, keepAliveInterval, mapper, 4 terminal callbacks)
- [ ] `DeliveredEvent<T>` public record with id/streamKey/timestamp/eventType/data/metadata
- [ ] `SseEventMapper<T>` generic interface with `map(DeliveredEvent<T>)` + default `terminal(TerminalReason)` method + `TerminalReason` enum + static `defaultMapper(ObjectMapper)` factory
- [ ] `PublisherCustomizer` marker interface extending `Consumer<PublisherConfig>`
- [ ] `SubscriberCustomizer` marker interface (exact shape pending open question)

### New internal types (package-private under `org.jwcarman.odyssey.engine`)

- [ ] `DefaultOdyssey` class implementing `Odyssey`
- [ ] `DefaultOdysseyPublisher<T>` class implementing `OdysseyPublisher<T>`
- [ ] `DefaultPublisherConfig` mutable class implementing `PublisherConfig`
- [ ] `DefaultSubscriberConfig<T>` mutable class implementing `SubscriberConfig<T>`
- [ ] `StoredEvent<T>` package-private record (`eventType`, `data`, `metadata`)
- [ ] `SseJournalAdapter<T>` package-private class — the only class that imports `SseEmitter`

### Deleted types (same package)

- [ ] `OdysseyStream`, `OdysseyStreamRegistry`, `OdysseyEvent`, `StreamSubscriberBuilder`, old `SseEventMapper`
- [ ] `DefaultOdysseyStream`, `DefaultOdysseyStreamRegistry`, `StreamSubscription`
- [ ] All corresponding old tests

### Behavioral requirements

- [ ] `Odyssey.publisher(...)` and its sugared variants call `createOrConnect` internally — try `JournalFactory.create`, catch `JournalAlreadyExistsException`, fall back to `JournalFactory.connect`. Cluster-safe across instances.
- [ ] `Odyssey.publisher(...)` is eager: one backend round-trip at call time, errors surface at the call site.
- [ ] `Odyssey.subscribe/resume/replay` is lazy on the facade side: zero I/O in the calling thread. First I/O happens in the `SseJournalAdapter` writer thread.
- [ ] `SseJournalAdapter` pattern-matches on `NextResult` and invokes `SseEventMapper.terminal(reason)` before `emitter.complete()` for each terminal variant.
- [ ] `SseJournalAdapter` distinguishes `NextResult.Errored` (emit terminal SSE event, then complete) from `IOException` during `emitter.send()` (client is gone — cancel source and exit silently).
- [ ] `OdysseyPublisher.close()` calls `journal.complete(cfg.retentionTtl())`; `close(Duration)` uses the override.
- [ ] `OdysseyPublisher.delete()` calls `journal.delete()`, and all active subscribers (in any JVM) receive `NextResult.Deleted` and complete their emitters.
- [ ] `PublisherCustomizer` beans are picked up from the Spring context and applied before the per-call customizer.
- [ ] Sugared methods (`ephemeral`, `channel`, `broadcast`) layer the category TTL from `OdysseyProperties` between the hardcoded defaults and the user customizers.

### Tests

- [ ] Publisher unit tests covering: create path, getOrCreate fallback path, publish with/without event type, close() with default retention, close(Duration) override, delete(), AutoCloseable in try-with-resources
- [ ] Subscriber unit tests covering: subscribe/resume/replay each producing emitters, customizer applied to config, terminal callbacks fire for Completed/Expired/Deleted/Errored, keep-alive on Timeout, exit-silently on IOException
- [ ] `SseJournalAdapter` unit tests with mocked `BlockingSubscription` — one per `NextResult` variant, plus the IOException path
- [ ] Customizer stacking test: Spring bean `PublisherCustomizer` runs before per-call customizer
- [ ] Cross-instance `InMemoryEndToEndTest` creating two `DefaultOdyssey` instances sharing the same `InMemoryJournalSpi` — both call `channel("shared", ...)`, both succeed, both see each other's events
- [ ] In-memory end-to-end test exercising producer/consumer split with a typed domain record (not `OdysseyEvent`)
- [ ] `odyssey-example` updated to the new API and starts successfully

### Documentation & build

- [ ] `README.md` updated with new usage examples (both publisher and subscriber sides)
- [ ] `CHANGELOG.md` has a "Breaking changes" section under `[Unreleased]` listing every removed type and every renamed method
- [ ] `./mvnw clean install` passes for the whole reactor
- [ ] No `@SuppressWarnings` added
- [ ] All existing backend starter modules still build unchanged (no changes to their POMs beyond what spec 044 already did)

## Implementation notes

### Phasing inside one branch

Do it in three commits so the build stays green the whole way:

1. **Add the new types alongside the old.** Introduce `Odyssey`, `OdysseyPublisher`, `PublisherConfig`, `SubscriberConfig`, `DeliveredEvent`, `StoredEvent`, new `SseEventMapper`, `SseJournalAdapter`, `DefaultOdyssey`, `DefaultOdysseyPublisher`. Wire `OdysseyAutoConfiguration` to produce *both* the new `Odyssey` bean and the existing `DefaultOdysseyStreamRegistry`. Add new tests. Old tests keep passing.
2. **Migrate `odyssey-example` to the new API** and delete the example's usage of the old types. Confirm the example starts and serves SSE.
3. **Delete the old API.** Remove `OdysseyStream`/`OdysseyStreamRegistry`/`OdysseyEvent`/old `SseEventMapper` and all their implementations and tests. Remove the `DefaultOdysseyStreamRegistry` bean from `OdysseyAutoConfiguration`. One final `./mvnw clean install`.

### Things to watch

- **`StoredEvent<T>` typing.** Substrate's `JournalFactory.create(name, typeRef, ttl)` takes a `TypeRef<T>` for generic payload types. `StoredEvent<T>` is generic, so the publisher needs to build a `TypeRef<StoredEvent<T>>` from the user's `Class<T>`. Codec's `TypeRef` likely supports this via `new TypeRef<StoredEvent<T>>() {}` pattern — verify when implementing.
- **Virtual thread naming.** Keep the `odyssey-writer-{streamKey}` naming for thread dumps. If the stream key contains characters unsafe for thread names, sanitize.
- **`SseEmitter` completion races.** Today's `StreamSubscription` uses `AtomicBoolean closed` to make `close()` idempotent. Preserve that pattern in `SseJournalAdapter` — the emitter's timeout/error/complete callbacks can fire concurrently with the writer loop's terminal path, and we must not double-complete the emitter.
- **Keep-alive timing.** Current code uses `long keepAliveInterval` in millis. New code should use `Duration` throughout — no raw longs in the public API.
- **Don't try to preserve wire-format compatibility.** A journal written by old-Odyssey (payload = `OdysseyEvent`) cannot be read by new-Odyssey (payload = `StoredEvent<T>`). Callers upgrading must drop their journals or migrate the data externally. Document this in CHANGELOG.

## Open questions

These are deliberately deferred — resolve during implementation or in code review, but don't let them block spec approval.

1. **`SubscriberCustomizer` generic shape.** A globally-applied customizer can't know `T`, so it can't set the mapper. Two options:

   - (a) Split the config: `SubscriberConfig` (non-generic, universal knobs) is what customizers see; `SubscriberConfig<T>` (extends it, adds `mapper(...)`) is what per-call customizers see.
   - (b) Keep one generic `SubscriberConfig<T>`, make `SubscriberCustomizer` a `Consumer<SubscriberConfig<?>>`, and document that globally-applied customizers must not call `mapper(...)` on a `?`-typed config.

   Option (a) is more type-safe. Option (b) is simpler. Pick during implementation.

2. **Hardcoded fallback TTLs.** `DefaultPublisherConfig` needs hardcoded fallbacks for users who don't set `OdysseyProperties` and don't wire a customizer. Current `odyssey-defaults.properties` has `channel-ttl=1h` etc. Reuse those numbers for the hardcoded fallbacks? Or pick conservative universal defaults (e.g., 1h for everything)? Either is fine — decide in review.

3. **Terminal SSE event format.** Current sketch uses `event: odyssey-completed` etc. (addressable via `addEventListener`). Alternative: use SSE comments (`: odyssey-completed\n`), which are invisible to naive clients but require custom parsing to detect. The `event:` approach is more useful; default to it unless there's a specific reason to hide the terminal state from default `onmessage` handlers.

4. **Does the default `SseEventMapper` need a `TypeRef` for polymorphic payloads?** If `T` is a sealed interface and users want the JSON to include the discriminator, the mapper needs to serialize via a proper `TypeRef`, not just `objectMapper.writeValueAsString(event.data())`. Might need to thread the `Class<T>` through to the mapper factory. Verify with a polymorphic test case during implementation.

5. **Exception from the customizer.** If the user's per-call customizer throws, what happens to the publisher/subscriber construction? Probably let the exception propagate — customizers are just `Consumer` and normal lambda exception semantics apply. Confirm with a test.
