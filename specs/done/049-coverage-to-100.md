# Push odyssey-core test coverage to ~100%

## What to build

Current overall coverage is **92.1%** (per SonarCloud). `odyssey-example` is excluded from coverage via `sonar.coverage.exclusions=odyssey-example/**`, so the number reflects `odyssey-core` only. Drive it as close to 100% as reasonable — aim for 100%, accept 99%+ if there's a legitimately unreachable branch (e.g., a defensive `IllegalStateException` throw that's provably impossible).

### Method

1. Run `./mvnw -pl odyssey-core clean verify` which produces a JaCoCo report at `odyssey-core/target/site/jacoco/index.html` (or the XML at `odyssey-core/target/site/jacoco/jacoco.xml`).
2. Parse the JaCoCo XML to find classes / methods / lines with uncovered instructions or branches.
3. For each uncovered fragment, either:
   - **Add a unit test** that exercises the code path (preferred)
   - **Delete the dead code** if it's genuinely unreachable (rare)
   - **Refactor** to make it testable if it's tangled with untestable parts
4. Re-run coverage, iterate until the report shows ≥99% instructions and ≥95% branches.

### Files likely to have coverage gaps

Based on file layout + complexity, educated guesses about where the missing ~8% lives:

- **`SseJournalAdapter`** — has multiple `catch` branches (`JournalExpiredException` mid-stream, unexpected `Exception`, `IOException` from send), some of which are hard to hit without threaded test tricks. The `trySendTerminal` inner try/catch is another target.
- **`DefaultOdysseyPublisher`** — the `close(Duration)` override and `delete()` methods may be tested via `DefaultOdysseyTest` but not directly.
- **`DefaultOdyssey`** — `createOrConnect` has both the happy path and the `JournalAlreadyExistsException` fallback; the fallback may be uncovered.
- **`SseEventMapper.defaultMapper`** — the Jackson serialization path may have uncovered branches (e.g., `eventType == null` vs non-null).
- **`DefaultSubscriberConfig`** — setters that return `this` may not all be covered.
- **`OdysseyAutoConfiguration`** — the bean factory method itself is covered by `OdysseyAutoConfigurationTest`, but if the autoconfig has branches (e.g., `@ConditionalOnMissingBean`), those may not be exercised.
- **`DeliveredEvent`** / **`OdysseyProperties`** — record accessors and validation are usually trivial, but there might be a null-guarding branch somewhere.

Don't assume — run the report first.

### Detailed acceptance plan

1. **Produce the coverage report.** `./mvnw -pl odyssey-core clean verify` (the jacoco plugin is already wired into the parent POM based on the `jacocoArgLine` property we saw earlier).
2. **Find uncovered lines.** Parse the XML or read the HTML report; produce a list of `(file, line, reason)`.
3. **Write tests for each uncovered path.** One test per distinct path is fine; bundle them into whichever existing test class fits best, or create a new one if the class has no existing tests.
4. **Re-run coverage.** Confirm the numbers moved.
5. **Iterate** until the target is hit.

### Exclusions

- Don't add tests for `odyssey-example` — it's excluded from coverage.
- Don't add tests for the backend starter modules (`odyssey-redis-spring-boot-starter` etc.) — they're pure POM-only modules with no Java code.
- Spring Boot boilerplate (`@Configuration` classes, `@Bean` factory methods) is worth testing via `ApplicationContextRunner` but doesn't need exhaustive branch coverage.

### Out of scope

- Integration tests against real backends — that's a different spec and not part of this coverage push
- `odyssey-example` coverage

## Acceptance criteria

- [ ] `./mvnw -pl odyssey-core clean verify` produces a JaCoCo report showing ≥99% instruction coverage and ≥95% branch coverage for `odyssey-core`
- [ ] SonarCloud `coverage` metric for `jwcarman_odyssey` is ≥99% (new code coverage already at 92.1%, total coverage should match after this spec lands)
- [ ] No `@SuppressWarnings` added to make code "look covered"
- [ ] No deleted production code unless it's provably unreachable (and the reason is documented in the deletion commit)
- [ ] All existing tests still pass

## Implementation notes

- JaCoCo's XML report format makes it easy to script: `odyssey-core/target/site/jacoco/jacoco.xml` has `<counter type="INSTRUCTION" missed="..." covered="..."/>` entries per class. Simple parsing script can list the worst offenders.
- Spring Boot autoconfig tests via `ApplicationContextRunner` are cheap to write and cover the `@Bean` factory methods cleanly.
- If a branch is legitimately defensive (e.g., a `null` check that's impossible under correct usage), it's better to keep the check AND add the test (supplying null on purpose) than to delete the check.
- For catch blocks that are hard to trigger (e.g., `IOException` during terminal frame send), use Mockito to make the emitter throw `IOException` on a specific call sequence. We already do this for mid-value sends; extend to terminal-frame sends.
- The SonarCloud quality gate condition `new_coverage < 80%` is already passing at 92.1%; the goal of this spec is the overall `coverage` metric, not just new-code coverage.
