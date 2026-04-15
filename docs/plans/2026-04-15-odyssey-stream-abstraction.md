# OdysseyStream Abstraction Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the string-and-type-per-call facade (`odyssey.publisher(...)`, `odyssey.subscribe(...)`, `odyssey.resume(...)`, `odyssey.replay(...)`) with a single factory method returning an `OdysseyStream<T>` handle that carries the stream's name, type, and TTL. Publish, subscribe, resume, replay, complete, and delete become methods on the handle.

**Why:** Name and TTL are properties of the stream itself, not of any individual caller. The current API forces every call site to restate them, which caused the entire "who owns TTL, who can create, what about typos" thread — see `docs/plans/discussion-2026-04-15-odyssey-stream-ux.md` if captured, otherwise the conversation is summarized in the design section below.

**Model:** Broker-style. `odyssey.stream(name, type, ttl)` is always get-or-create. First caller establishes the stream at substrate with the given TTL; subsequent callers (same process or otherwise) silently adopt. TTL mismatch on later calls is ignored — matches Kafka / ActiveMQ / NATS JetStream conventions. Type is caller-asserted; mismatches surface as deserialization errors.

**Scope:** Breaking API change. Target release: Odyssey 0.10.0. `OdysseyPublisher`, `PublisherCustomizer`, and the facade's publish/subscribe/resume/replay methods are all deleted. `SubscriberCustomizer` moves to per-call parameters on `OdysseyStream<T>`.

**Tech Stack:** Java 25, Maven, Spring Boot 4.x, JUnit 5, AssertJ, Mockito.

---

## Design reference

### New public surface

```java
public interface Odyssey {
  <T> OdysseyStream<T> stream(String name, Class<T> type, TtlPolicy ttl);
  <T> OdysseyStream<T> stream(String name, Class<T> type); // uses properties.defaultTtl()
}

public interface OdysseyStream<T> {
  String name();

  String publish(T data);
  String publish(String eventType, T data);

  SseEmitter subscribe();
  SseEmitter subscribe(SubscriberCustomizer<T> customizer);
  SseEmitter resume(String lastEventId);
  SseEmitter resume(String lastEventId, SubscriberCustomizer<T> customizer);
  SseEmitter replay(int count);
  SseEmitter replay(int count, SubscriberCustomizer<T> customizer);

  void complete();
  void delete();
}
```

### Deleted surface

- `Odyssey.publisher(...)` (both overloads)
- `Odyssey.subscribe(...)` (both overloads)
- `Odyssey.resume(...)` (both overloads)
- `Odyssey.replay(...)` (both overloads)
- `OdysseyPublisher<T>` interface and `DefaultOdysseyPublisher<T>` class
- `PublisherCustomizer` interface and `DefaultPublisherConfig` / `PublisherConfig`
- `SseJournalAdapter`'s `JournalNotFoundException` → 404 translation (unreachable under broker semantics)
- The three `*Throws404WhenJournalNotFound` tests in `DefaultOdysseyTest`

### Pinned decisions

1. **TTL is stream-scoped, set at first creation.** Subsequent `stream(...)` calls' TTL argument is silently ignored. This matches substrate's existing `AlreadyExistsException → connect` fallback; we make it the primary model instead of an edge case.
2. **Type is caller-asserted, not enforced.** Odyssey holds no registry and does not read any metadata atom. Deserialization errors at `publish`/`subscribe` time are the only signal.
3. **Handles are not cached by Odyssey.** Each `odyssey.stream(...)` call returns a fresh `OdysseyStream<T>` wrapping a fresh substrate `Journal<StoredEvent>` handle. Callers cache at the application layer if they want to.
4. **No `declare` vs `lookup` split.** One method, always get-or-create, broker semantics.
5. **`complete()` and `delete()` live on the handle.** They mutate stream-level state, not connection-level. Callers must be aware these are destructive across all current subscribers.

---

## Pre-flight

Confirm main is green:

```
./mvnw clean verify
```

Confirm substrate dependency is `0.6.0-SNAPSHOT` (already set in `pom.xml`). No bump needed.

Create branch `odyssey-stream-abstraction`.

---

## Phase A — Introduce `OdysseyStream<T>` alongside existing API

Build the new type without deleting anything yet. Existing tests must keep passing.

### Task A1: Create `OdysseyStream<T>` interface

**File:** `odyssey/src/main/java/org/jwcarman/odyssey/core/OdysseyStream.java`

**Step 1:** Write the interface as specified in the design reference above. Javadoc each method. `publish` methods return the SSE event id (String). `complete` and `delete` return void.

**Step 2:** Compile: `./mvnw -pl odyssey compile`. Expected: PASS.

### Task A2: Create `DefaultOdysseyStream<T>` implementation

**File:** `odyssey/src/main/java/org/jwcarman/odyssey/engine/DefaultOdysseyStream.java`

**Step 1:** Port logic from `DefaultOdysseyPublisher` for the publish/complete/delete side. Fields: `Journal<StoredEvent> journal`, `String name`, `Class<T> type`, `TtlPolicy ttl`, `ObjectMapper objectMapper`, `OdysseyProperties properties`.

**Step 2:** Port subscribe/resume/replay from `DefaultOdyssey`'s current methods — the `createSubscriberConfig` + `startAdapter` helpers move into this class. Existing `SseJournalAdapter` stays unchanged.

**Step 3:** Compile: `./mvnw -pl odyssey compile`. Expected: PASS.

### Task A3: Add `Odyssey.stream(...)` factory methods

**File:** `odyssey/src/main/java/org/jwcarman/odyssey/core/Odyssey.java`

**Step 1:** Add the two `stream(...)` methods to the interface.

**File:** `odyssey/src/main/java/org/jwcarman/odyssey/engine/DefaultOdyssey.java`

**Step 2:** Implement with `createOrConnect` calling `new DefaultOdysseyStream<>(...)`. Keep existing methods intact for now (they'll be deleted in Phase C).

**Step 3:** Compile and run tests: `./mvnw -pl odyssey test`. Expected: PASS — no existing behavior changed.

### Task A4: Unit-test `DefaultOdysseyStream<T>`

**File:** `odyssey/src/test/java/org/jwcarman/odyssey/engine/DefaultOdysseyStreamTest.java`

**Step 1:** Tests parallel to `DefaultOdysseyPublisherTest` for the publish side. Cover: `publish(data)`, `publish(eventType, data)`, `complete()`, `delete()`, `name()` round-trips verbatim.

**Step 2:** Tests parallel to the subscribe/resume/replay tests in `DefaultOdysseyTest` for the read side. Cover: each method returns a non-null emitter; customizer is applied; keep-alive and timeout are sourced from properties.

**Step 3:** Run: `./mvnw -pl odyssey test`. Expected: PASS.

### Task A5: End-to-end test using the new API

**File:** `odyssey/src/test/java/org/jwcarman/odyssey/engine/StreamHandleEndToEndTest.java`

**Step 1:** Mirror `InMemoryEndToEndTest` but exclusively through `odyssey.stream(...)` handles. Cover the publish-then-subscribe golden path, resume with `Last-Event-ID`, replay last N, `complete()`, and `delete()`.

**Step 2:** Run: `./mvnw -pl odyssey test`. Expected: PASS.

---

## Phase B — Migrate example app to new API

Prove the UX on real call sites before deleting the old API.

### Task B1: Introduce `Streams` factory

**File:** `odyssey-example/src/main/java/org/jwcarman/odyssey/example/Streams.java`

**Step 1:** Create a `@Component` exposing typed methods:

```java
OdysseyStream<Announcement> announcements();
OdysseyStream<Notification> userChannel(String userId);
OdysseyStream<Progress> taskProgress(String taskId);
```

Each wraps `odyssey.stream(name, type, appropriateTtl)`. Names and TTLs are no longer controller concerns.

**Step 2:** Compile: `./mvnw -pl odyssey-example compile`. Expected: PASS.

### Task B2: Migrate `BroadcastController`

**Step 1:** Inject `Streams` instead of `Odyssey`. Remove the `@PostConstruct materializeBroadcastStream` hack — get-or-create semantics make it unnecessary.

**Step 2:** Replace body of `publish(...)` with `streams.announcements().publish("message", new Announcement(...))`.

**Step 3:** Replace `subscribe(...)` body with `s.resume(lastEventId)` / `s.subscribe()` on the handle from `streams.announcements()`.

**Step 4:** Run: `./mvnw -pl odyssey-example test`. Expected: PASS.

### Task B3: Migrate `NotifyController`

**Step 1:** Inject `Streams`. Remove the "pre-materialize on subscribe" call.

**Step 2:** Replace publish and subscribe/resume bodies with `streams.userChannel(userId)` calls.

**Step 3:** Run: `./mvnw -pl odyssey-example test`. Expected: PASS.

### Task B4: Migrate `TaskController`

**Step 1:** Inject `Streams`. Replace `odyssey.publisher(taskId, Progress.class, cfg -> cfg.ttl(EPHEMERAL))` with `streams.taskProgress(taskId)`.

**Step 2:** Update the virtual-thread publisher loop and the subscribe endpoint.

**Step 3:** Run: `./mvnw -pl odyssey-example test`. Expected: PASS.

### Task B5: Manually verify the example app

**Step 1:** Run `./mvnw -pl odyssey-example spring-boot:run`.

**Step 2:** Open the static HTML pages (broadcast, notify, task) and exercise publish/subscribe/resume on each. Subscribe before publish on broadcast — should tail silently, not 404.

**Step 3:** Confirm no regressions vs current behavior.

---

## Phase C — Delete old API

Now that the new API is proven in tests and the example app, remove the legacy surface.

### Task C1: Delete `OdysseyPublisher` and friends

**Files to delete:**
- `odyssey/src/main/java/org/jwcarman/odyssey/core/OdysseyPublisher.java`
- `odyssey/src/main/java/org/jwcarman/odyssey/core/PublisherCustomizer.java`
- `odyssey/src/main/java/org/jwcarman/odyssey/core/PublisherConfig.java` (if present)
- `odyssey/src/main/java/org/jwcarman/odyssey/engine/DefaultOdysseyPublisher.java`
- `odyssey/src/main/java/org/jwcarman/odyssey/engine/DefaultPublisherConfig.java`
- `odyssey/src/test/java/org/jwcarman/odyssey/engine/DefaultOdysseyPublisherTest.java`
- `odyssey/src/test/java/org/jwcarman/odyssey/engine/DefaultPublisherConfigTest.java`

**Step 1:** Remove the files.

**Step 2:** Compile: `./mvnw -pl odyssey compile`. Expect references in `Odyssey` / `DefaultOdyssey` to break — Task C2 handles that.

### Task C2: Remove legacy methods from facade

**Files:** `odyssey/src/main/java/org/jwcarman/odyssey/core/Odyssey.java`, `odyssey/src/main/java/org/jwcarman/odyssey/engine/DefaultOdyssey.java`

**Step 1:** Delete `publisher(...)`, `subscribe(...)`, `resume(...)`, `replay(...)` methods from both. `DefaultOdyssey` shrinks to just the two `stream(...)` methods plus `createOrConnect` private helper.

**Step 2:** Compile: `./mvnw -pl odyssey compile`. Expected: PASS.

### Task C3: Remove 404 translation and its tests

**File:** `odyssey/src/main/java/org/jwcarman/odyssey/engine/SseJournalAdapter.java`

**Step 1:** Remove the `JournalNotFoundException` catch block and its imports (`JournalNotFoundException`, `HttpStatus`, `ResponseStatusException`). Under broker semantics the exception is unreachable — every `stream(...)` call `createOrConnect`s, so the journal exists by the time any subscribe fires.

**File:** `odyssey/src/test/java/org/jwcarman/odyssey/engine/DefaultOdysseyTest.java`

**Step 2:** Delete `subscribeThrows404WhenJournalNotFound`, `resumeThrows404WhenJournalNotFound`, `replayThrows404WhenJournalNotFound`.

**Step 3:** Run: `./mvnw -pl odyssey test`. Expected: PASS.

### Task C4: Trim `DefaultOdysseyTest` to `stream(...)` tests

**Step 1:** Rewrite the file: tests now cover `odyssey.stream(name, type, ttl)` — returns non-null handle, name is verbatim, default-TTL overload uses properties. All per-call publisher/subscriber tests move to `DefaultOdysseyStreamTest` (already done in Task A4).

**Step 2:** Run: `./mvnw -pl odyssey test`. Expected: PASS.

---

## Phase D — Polish

### Task D1: CHANGELOG

**File:** `CHANGELOG.md`

**Step 1:** Under `## [Unreleased]`, add `### Breaking changes` section documenting:
- `Odyssey.publisher/subscribe/resume/replay` removed. Use `Odyssey.stream(name, type, ttl)` which returns `OdysseyStream<T>`.
- `OdysseyPublisher` and `PublisherCustomizer` deleted. TTL is now a stream-level property, set at first `stream(...)` call.
- Behavior change: cold-start subscribers no longer see an empty hang on a never-published stream; `stream(...)` always materializes the journal (broker semantics).

### Task D2: README update

**Step 1:** Update all code examples in `README.md` to the new API. The `Streams` factory pattern is worth calling out as the idiomatic usage.

### Task D3: Full verify

**Step 1:** `./mvnw clean verify`. Expected: PASS across all modules including the example.

**Step 2:** `./mvnw spotless:check`. Expected: PASS.

**Step 3:** `./mvnw -P release javadoc:jar -DskipTests`. Expected: no doclint errors on the new `OdysseyStream` / `Odyssey` javadoc.

---

## Out of scope

- **SubscriberCustomizer shape.** Stays as-is. Only its location changed (now per-call on the handle, same as before).
- **Access-control views** (publish-only, subscribe-only handles). Deferred until a real caller needs them; add `asPublisher()` / `asSubscriber()` on `OdysseyStream<T>` if that day comes.
- **Metadata atom for cross-JVM type enforcement.** Deferred indefinitely per pinned decision 2.
- **Substrate `open(...)` primitive.** Separate conversation with substrate; not blocking this work.

## Risks

- **Example HTML clients may break** if they assume behavior that's changed (e.g., the broadcast page currently retries forever on 404 — under broker semantics it'll just tail). Task B5 catches this manually.
- **User-facing breaking change** at 0.10.0. Migration is mechanical but unavoidable. CHANGELOG and README carry the weight.
- **Lost feature parity** on `publisher(name, type, customizer)` where the customizer set more than TTL. Confirm TTL was the only customizer knob before executing Phase C.
