# Test Sonar polish: kill all test-file code smells

## What to build

Fix all SonarCloud issues in `odyssey-core/src/test`. These are the noisy ones — unused imports, unused variables, empty test methods — left over from the several rounds of test rewrites during the API redesign.

### Issues in scope

| Rule | Severity | File | Action |
|---|---|---|---|
| `java:S2699` | BLOCKER | `SseJournalAdapterTest.java:214` | The `closeIsIdempotent` test has no real assertion — just calls `adapter.close()` twice and comments "No exception = idempotent". Add a Mockito assertion that `source.cancel()` (or similar side effect) is invoked exactly once. |
| `java:S1128` | MINOR | `SseJournalAdapterTest.java:37` | Remove unused import of `TerminalState`. |
| `java:S1186` | CRITICAL | `SseJournalAdapterTest.java:57` | Empty `@BeforeEach setUp()` method. Just delete it — JUnit doesn't care if there's no setup. |
| `java:S1481` | MINOR (×4 in tests) | `SseEventMapperTest.java:88,89,90` and `SseJournalAdapterTest.java:215` | Unused pattern-match bindings in the `switch` inside `terminalCanBeOverriddenToEmitAFrame` — change `case TerminalState.Completed c -> ...` to `case TerminalState.Completed _ -> ...` (unnamed pattern). Same for the `cancelled` local in `SseJournalAdapterTest`. |
| `java:S1854` | MAJOR | `SseJournalAdapterTest.java:215` | `cancelled` local variable is assigned but never read. Delete it along with fixing S2699 above. |
| `java:S5778` | MAJOR | `DeliveredEventTest.java:53` | A lambda passed to `assertThatThrownBy` (or similar) has multiple statements that might throw. Refactor the test so the lambda has a single operation that might throw. |
| `java:S6068` | MINOR | `DefaultOdysseyTest.java:104,118` | `journalFactory.create(startsWith("ephemeral:"), eq(OdysseyEvent.class), eq(EPHEMERAL_TTL))` — the `eq(...)` wrappers around non-matcher arguments are redundant; pass the values directly. (This one might be from the old test file or from DefaultOdysseyTest after the redesign — check both.) |

### Detailed fixes

#### `SseJournalAdapterTest.closeIsIdempotent` (S2699, S1854, S1481)

Current test:

```java
@Test
void closeIsIdempotent() {
  AtomicBoolean cancelled = new AtomicBoolean(false);
  SseJournalAdapter<TestData> adapter = createAdapter(defaultConfig());
  adapter.close();
  adapter.close();
  // No exception = idempotent
}
```

Problems:
- `cancelled` is assigned but never read
- No real assertion — the test just proves that calling `close()` twice doesn't throw, which is trivially true if the method is empty
- This test also depends on the current "close() handles null source" behavior, which goes away in spec 046's refactor

After spec 046, the adapter's `source` is final and non-null by construction. You can only call `close()` on an adapter that was fully constructed, which requires a real `BlockingSubscription` in the constructor. The test should be rewritten to verify that `source.cancel()` is called exactly once across multiple `close()` invocations:

```java
@Test
void closeIsIdempotent() {
  DefaultSubscriberConfig<TestData> config = defaultConfig();
  // Construct with a mocked, already-present source — mimicking what launch() does internally
  SseJournalAdapter<TestData> adapter = newAdapterWithSource(source, config);

  adapter.close();
  adapter.close();
  adapter.close();

  verify(source, times(1)).cancel();
}
```

Where `newAdapterWithSource` is a test helper that calls the package-private constructor directly (spec 046 will expose it as package-private for this purpose).

#### `SseJournalAdapterTest` empty `setUp` (S1186)

```java
@BeforeEach
void setUp() throws Exception {}
```

Delete it. There's no setup code. JUnit doesn't need the annotation if there's nothing to do.

#### `SseEventMapperTest` unused pattern bindings (S1481 ×3)

Current:

```java
return switch (state) {
  case TerminalState.Completed c -> Optional.of(SseEmitter.event().name("done"));
  case TerminalState.Expired e -> Optional.of(SseEmitter.event().name("expired"));
  case TerminalState.Deleted d -> Optional.of(SseEmitter.event().name("deleted"));
  case TerminalState.Errored err ->
      Optional.of(SseEmitter.event().name("errored").data(err.cause().getMessage()));
};
```

The first three bindings (`c`, `e`, `d`) aren't used. Replace with empty record patterns:

```java
return switch (state) {
  case TerminalState.Completed() -> Optional.of(SseEmitter.event().name("done"));
  case TerminalState.Expired() -> Optional.of(SseEmitter.event().name("expired"));
  case TerminalState.Deleted() -> Optional.of(SseEmitter.event().name("deleted"));
  case TerminalState.Errored(Throwable cause) ->
      Optional.of(SseEmitter.event().name("errored").data(cause.getMessage()));
};
```

Empty-parens record patterns for zero-field records, and destructure the `Throwable` for `Errored`.

#### `DeliveredEventTest.java:53` S5778 multi-throw lambda

Whatever test is at line 53 has an `assertThatThrownBy(() -> { ...; ...; })` where multiple statements can throw. Narrow the lambda to the single throwing operation. Fetch the file to see the actual code and refactor.

#### `DefaultOdysseyTest.java:104,118` S6068 redundant eq()

Current (likely):

```java
when(journalFactory.create(startsWith("ephemeral:"), eq(OdysseyEvent.class), eq(EPHEMERAL_TTL)))
    .thenReturn(journal);
```

The rule: you can't mix matchers and raw values. When you use ONE matcher, you must use matchers for all arguments. BUT when all arguments are values, you should use values directly. Sonar flags the case where you use `eq(value)` unnecessarily — probably because one of the other arguments became a raw value at some point. Fix:

```java
// If all args are matchers:
when(journalFactory.create(startsWith("ephemeral:"), eq(OdysseyEvent.class), eq(EPHEMERAL_TTL)))
    .thenReturn(journal);

// Or (better, if Mockito allows mixing because of argThat → any(Class) equivalence):
// actually Mockito requires all-matchers-or-all-values — so keep the eq() wrappers.
```

Actually, Sonar S6068 specifically fires when you have `any()` matchers alongside `eq()` matchers where the `eq()` is redundant because the arg is already constrained by the context. Check the exact issue context. If it's a mix like `any(String.class), eq(OdysseyEvent.class), eq(EPHEMERAL_TTL)` where `any(String.class)` could be `anyString()`, or where the whole thing could use `any()` without class specifiers, refactor accordingly.

**Action:** fetch the file, look at the specific lines, pick the simplest refactor that satisfies Mockito + Sonar.

### Out of scope

- Main-code issues (spec 046)
- Example-app issues (spec 048)
- Coverage gaps (spec 049)

## Acceptance criteria

- [ ] `SseJournalAdapterTest.closeIsIdempotent` has a real assertion verifying `source.cancel()` is called exactly once
- [ ] `SseJournalAdapterTest.setUp()` empty method deleted
- [ ] `SseJournalAdapterTest` has no unused imports
- [ ] `SseEventMapperTest.terminalCanBeOverriddenToEmitAFrame` uses empty record patterns for the three zero-field variants and destructures `Throwable cause` for `Errored`
- [ ] `DeliveredEventTest.java:53` lambda has only one throwing operation (the assertion target)
- [ ] `DefaultOdysseyTest.java:104,118` Mockito stubbings use the idiomatic matcher combination (no redundant `eq()`)
- [ ] `./mvnw clean install` passes
- [ ] All existing odyssey-core tests still pass (test count may change slightly due to the `closeIsIdempotent` rewrite, but no regressions)
- [ ] SonarCloud shows zero open issues for rules S2699, S1128, S1186 (in `SseJournalAdapterTest.java`), S1481 (in test files), S1854, S5778, S6068 after push

## Implementation notes

- Fetch the actual files before editing — the exact shape of each issue matters. Line numbers in the Sonar report may shift as other changes land.
- For S5778 in `DeliveredEventTest`, the fix is almost always "move the setup code out of the lambda and leave only the single throwing call inside."
- For S6068 in `DefaultOdysseyTest`, the fix depends on Mockito's strict matcher rules. If the stubbing has a mix of matchers and raw values, either convert all to matchers (add `eq()` where needed) or all to raw values (remove `eq()`). The Sonar rule fires when there's a redundant conversion.
