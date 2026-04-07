# Replace Thread.sleep() with Awaitility in tests

## What to build

Replace all `Thread.sleep()` calls in test code with Awaitility assertions. This
eliminates flaky tests caused by arbitrary sleep durations and makes async assertions
explicit and self-documenting.

### Add Awaitility dependency

Add to the parent POM as a test dependency (Spring Boot manages the version):

```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

### Replace patterns

**Before:**
```java
Thread.sleep(200);
assertEquals(2, queue.size());
```

**After:**
```java
await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(2, queue.size()));
```

**Before:**
```java
Thread.sleep(100);
verify(handler).onEvent(any());
```

**After:**
```java
await().atMost(5, SECONDS).untilAsserted(() -> verify(handler).onEvent(any()));
```

**Before (waiting for a thread to do something):**
```java
subscriber.nudge();
Thread.sleep(200);
assertFalse(thread.isAlive());
```

**After:**
```java
subscriber.nudge();
await().atMost(5, SECONDS).until(() -> !thread.isAlive());
```

### Scan all test files

Find every `Thread.sleep()` in test code across all modules and replace with the
appropriate Awaitility pattern. Common locations:

- `StreamReaderTest`
- `StreamSubscriberTest`
- `DefaultOdysseyStreamTest`
- `InMemoryEndToEndTest`
- Integration tests (`*IT`)

### Also replace CountDownLatch patterns where appropriate

Where a `CountDownLatch` is used solely to wait for async completion, Awaitility can
be cleaner:

**Before:**
```java
CountDownLatch latch = new CountDownLatch(1);
handler = (k, v) -> latch.countDown();
notifier.notify("key", "id");
assertTrue(latch.await(10, SECONDS));
```

**After (if the latch pattern is simple enough):**
```java
List<String> received = new CopyOnWriteArrayList<>();
handler = (k, v) -> received.add(k);
notifier.notify("key", "id");
await().atMost(10, SECONDS).until(() -> !received.isEmpty());
```

Use judgment — `CountDownLatch` is fine for complex synchronization. Only replace
when Awaitility is clearer.

## Acceptance criteria

- [ ] Awaitility is a test dependency in the parent POM
- [ ] No `Thread.sleep()` calls remain in any test code
- [ ] All replacements use `await().atMost(...)` with reasonable timeouts
- [ ] No test is flaky — all pass reliably on `./mvnw verify`
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Spring Boot manages the Awaitility version — do not specify a version.
- Use `import static org.awaitility.Awaitility.await` for clean syntax.
- Default poll interval is 100ms which is fine for most cases.
- Keep timeouts generous (5-10 seconds) to avoid CI flakiness.
- Do not replace `Thread.sleep()` in production code (e.g., reconnect backoff) —
  only test code.
