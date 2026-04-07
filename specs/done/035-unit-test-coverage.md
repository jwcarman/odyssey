# Achieve 100% unit test coverage

## What to build

Add unit tests to achieve 100% line and branch coverage across all modules. This
library is being prepared for Maven Central release and must have comprehensive test
coverage. Every file, every branch, every line. Use mocks/stubs — no Testcontainers
or Docker in these tests.

Auto-configuration classes included — use `ApplicationContextRunner` to test bean
creation, conditional logic, and property binding.

### AbstractOdysseyEventLog (71.4%)

- Test `ephemeralKey()` returns unique values with correct prefix
- Test `channelKey(name)` and `broadcastKey(name)` prepend correct prefix
- Test `generateEventId()` returns monotonically increasing, non-null UUIDs
- Test that two rapid calls to `generateEventId()` produce different values

### SnsOdysseyStreamNotifier (78.7%)

- Unit test `parseAndDispatch` with valid payloads
- Unit test `parseAndDispatch` with malformed payloads (no delimiter)
- Unit test `extractSnsMessage` with SNS JSON envelope
- Unit test `extractSnsMessage` with raw body (no envelope)
- Unit test `extractSnsMessage` with escaped characters in the message
- Test `subscribe` adds handler and handler receives dispatched notifications
- These methods may need to be made package-private or extracted to a helper
  class to be unit-testable without starting the poll loop

### PostgresOdysseyStreamNotifier (80.0%)

- Unit test `dispatchNotification` with valid payload
- Unit test `dispatchNotification` with malformed payload (no delimiter)
- Unit test that channel name validation rejects invalid names
- Unit test that channel name validation accepts valid names

### StreamWriter (80.6%)

- Test error handling: when `handler.onEvent()` throws, verify `handler.onError()`
  is called
- Test that `InterruptedException` during `queue.poll()` exits the loop cleanly

### DefaultOdysseyStream (81.9%)

- Test `publishRaw` calls `eventLog.append()` and `notifier.notify()`
- Test `publishJson` serializes the object and calls `eventLog.append()`
- Test `close()` calls `subscriberGroup.shutdown()`
- Test `delete()` calls `subscriberGroup.shutdownNow()` and `eventLog.delete()`
- Test `getStreamKey()` returns the correct key
- Test `subscribe()`, `resumeAfter()`, `replayLast()` create subscribers correctly

### DefaultOdysseyStreamRegistry (86.2%)

- Test `stream(streamKey)` returns a cached instance
- Test `stream(streamKey)` with a new key creates a new stream

### StreamSubscriberGroup (88.9%)

- Test `removeSubscriber` after `closeGracefully` doesn't throw
- Test `nudgeAll` with empty group is a no-op

### StreamReader (91.7%)

- Cover any remaining uncovered branch (likely the `InterruptedException` path
  during `acquire()`)

### Auto-configuration classes (50-92%)

For each auto-configuration class, use `ApplicationContextRunner` to test:
- Bean is created when expected conditions are met
- Bean is NOT created when conditions are not met
- Property values are correctly bound and passed to constructors
- `@ConditionalOnClass` and `@ConditionalOnMissingBean` behavior

Files: `NatsNotifierAutoConfiguration`, `SnsNotifierAutoConfiguration`,
`CassandraEventLogAutoConfiguration`, `PostgresEventLogAutoConfiguration`,
`DynamoDbEventLogAutoConfiguration`, `RabbitMqNotifierAutoConfiguration`,
`RabbitMqEventLogAutoConfiguration`, `NatsEventLogAutoConfiguration`,
`MongoEventLogAutoConfiguration`.

### All other files

Scan every source file for uncovered lines and branches. Add tests for:
- Error handling paths (catch blocks, error callbacks)
- Edge cases (null inputs, empty collections, boundary values)
- All conditional branches (if/else, switch, ternary)
- Cleanup/shutdown paths

## Acceptance criteria

- [ ] 100% line coverage target on all files (allow minor exceptions for
      unreachable code like `default` in exhaustive switches)
- [ ] All tests are `*Test.java` (Surefire) — no Testcontainers or Docker
- [ ] Use Mockito for mocking external dependencies
- [ ] Use `ApplicationContextRunner` for auto-configuration tests
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes
- [ ] Run `./mvnw -Pci verify` and verify JaCoCo reports show improvement

## Implementation notes

- For `SnsOdysseyStreamNotifier`, the JSON parsing methods (`extractSnsMessage`,
  `parseAndDispatch`) are private. Either make them package-private for testing,
  or extract them into a package-private helper class.
- For `PostgresOdysseyStreamNotifier`, `dispatchNotification` is already private.
  Same approach — make package-private or extract.
- Do not sacrifice encapsulation for coverage on trivial methods. If a method is
  a one-liner getter, skip it.
