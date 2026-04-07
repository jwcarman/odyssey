# SonarCloud cleanups

## What to build

Fix all remaining SonarCloud issues. 91 issues across the codebase, grouped by rule.

### 1. S7467 — Unnamed patterns (20 issues)

Replace `catch (SomeException e)` with `catch (SomeException _)` when the exception
variable is not referenced in the catch block. Java 25 supports unnamed patterns.

Files: NatsOdysseyEventLog, NatsOdysseyEventLogIT, CassandraOdysseyEventLog,
SnsOdysseyStreamNotifier, PostgresOdysseyStreamNotifier, RabbitMqOdysseyStreamNotifier,
RedisOdysseyStreamNotifier, StreamSubscriber, DefaultOdysseyStream, and others.

### 2. S1874 — Deprecated APIs (13 issues)

The SNS integration tests use the deprecated `LocalStackContainer` from
`org.testcontainers.containers.localstack`. Testcontainers 2.x moved it. Update to
the new package and non-generic class.

Also 2 deprecated MongoDB API calls in MongoOdysseyEventLog (S5738).

### 3. S1192 — Duplicate string literals (8 issues)

Extract constants for repeated string literals:
- `MongoOdysseyEventLog`: "streamKey" (6x), "eventId" (6x), "metadata" (3x)
- `RabbitMqOdysseyEventLog`: "timestamp" (3x)
- `TaskController` (example): "progress" (3x)
- `DynamoDbOdysseyEventLog`: "stream_key" (duplicated), "event_id" (duplicated)

### 4. S112 — Generic exceptions (6 issues)

`NatsOdysseyEventLog` throws/catches `RuntimeException` in several places. Replace
with specific exceptions (e.g., `OdysseyEventLogException` or wrap the original
cause properly).

Also `CassandraEventLogAutoConfiguration` throws `RuntimeException`.

### 5. S3077 — Volatile Thread/Connection fields (5 issues)

`volatile Thread pollerThread`, `volatile Thread listenerThread`,
`volatile Connection listenConnection` etc. SonarCloud says volatile is not enough
for thread safety on object references. These are used for interrupt/cleanup only.
Consider using `AtomicReference<Thread>` instead.

Files: RabbitMqOdysseyStreamNotifier, SnsOdysseyStreamNotifier,
NatsOdysseyStreamNotifier, PostgresOdysseyStreamNotifier.

### 6. S1186 — Empty methods in test stubs (3 issues)

`OdysseyAutoConfigurationTest` has empty stub methods (no-op implementations of
interfaces). Add `// no-op` comments to make the intent explicit.

### 7. S107 — Too many constructor parameters (2 issues)

- `RedisOdysseyEventLog`: 8 params
- `DefaultOdysseyStream`: 8 params

Consider using a builder or a config record to reduce parameter count.

### 8. S127 — Loop counter modified inside loop body (2 issues)

`SnsOdysseyStreamNotifier` lines 281, 297 — the JSON parser loop modifies `i`
inside the body to skip escaped characters. Refactor to use a while loop or
extract a method.

### 9. S6877 — Use `.reversed()` instead of `Collections.reverse()` (2 issues)

- `MongoOdysseyEventLog`: use `docs.reversed().stream()` instead of
  `Collections.reverse(reversed); reversed.stream()`
- `RedisOdysseyEventLog`: same pattern with `messages.reversed()`

### 10. S6068 — Unnecessary `eq()` in NATS tests (2 issues)

`NatsOdysseyStreamNotifierTest` lines 58, 68 — remove unnecessary `eq()` wrappers.

### 11. S6244 — DynamoDB builder style (2 issues)

Use Consumer Builder methods instead of nested builders in `DynamoDbOdysseyEventLog`.

### 12. S135 — Multiple break/continue in SNS poll loop (1 issue)

`SnsOdysseyStreamNotifier` line 198 — simplify the poll loop to use at most one
break or continue.

### 13. S1450 — Field should be local variable (1 issue)

`SnsOdysseyStreamNotifier.queueArn` — only used in `start()` and `stop()`. Make it
local or reorganize.

### 14. S125 — Commented-out code (1 issue)

`SnsOdysseyStreamNotifier` line 254 — remove commented-out code block.

### 15. S3776 — Cognitive complexity (1 issue)

`NatsOdysseyEventLog` line 248 — method has complexity 16, max allowed 15. Extract
a helper method.

### 16. S5778 — Multiple throwing calls in test lambda (1 issue)

`OdysseyEventTest` line 76 — refactor lambda to have only one potentially-throwing
invocation.

### 17. S6353 — Regex character class (1 issue)

`PostgresOdysseyStreamNotifier` line 41 — use `\\w` instead of `[a-zA-Z0-9_]`.

### 18. S899 — Unchecked `offer()` return (1 issue)

`StreamSubscriber` line 67 — the POISON `offer()` return value is unchecked. Use
`put()` instead (consistent with how the reader puts events).

## Acceptance criteria

- [ ] All 91 SonarCloud issues fixed
- [ ] No unnamed exception variables remain
- [ ] No deprecated API calls
- [ ] No duplicate string literals — all extracted to constants
- [ ] No generic exception throws
- [ ] Volatile Thread fields replaced with AtomicReference
- [ ] All empty test methods have comments
- [ ] `.reversed()` used instead of `Collections.reverse()`
- [ ] SNS JSON parser refactored (no loop counter modification)
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- The S3077 volatile Thread fields: `AtomicReference<Thread>` is the cleanest fix.
  Use `threadRef.get().interrupt()` with a null check.
- The S107 constructor params: consider grouping related params into a record.
  For `RedisOdysseyEventLog`, the three prefixes already come from the abstract class.
  The TTLs could be a record.
- The S125 commented-out code in SNS: just delete it.
- The S1450 `queueArn`: it's used in `start()` to set the policy and in `stop()` is
  not used. Make it a local in `start()`.
