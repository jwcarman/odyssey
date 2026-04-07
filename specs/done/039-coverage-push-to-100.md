# Push test coverage toward 100%

## What to build

Add tests to cover all remaining uncovered lines and branches. Target: 100% line
coverage on every file listed below. Use unit tests with mocks where possible,
integration tests with Testcontainers where necessary.

### Auto-configuration classes (50%)

**`NatsNotifierAutoConfiguration`** (50%, 2 uncovered lines)
**`SnsNotifierAutoConfiguration`** (50%, 3 uncovered lines, 3 uncovered conditions)

These are bean factory methods. Use `ApplicationContextRunner` to test:
- Bean is created when the right class is on the classpath
- Beans are wired correctly with properties
- Test both the connection/client bean and the notifier bean

### NatsOdysseyEventLog (78.2%, 25 uncovered lines, 16 conditions)

This has the most gaps. Cover:
- Error handling paths (catch blocks for JetStream exceptions)
- `readAfter` and `readLast` edge cases (empty stream, stream doesn't exist)
- `delete` / purge path
- The `close()` / `AutoCloseable` path
- All conditional branches

### RabbitMqOdysseyEventLog (81.8%, 13 uncovered lines, 13 conditions)

Cover:
- Error handling in `append`, `readAfter`, `readLast`
- Stream creation failure paths
- `close()` cleanup
- Edge cases: empty reads, non-existent streams

### PostgresOdysseyEventLog (85.6%, 7 uncovered lines, 12 conditions)

Cover:
- The trimming logic (trigger a trim by setting small `maxLen` and appending
  enough events to hit the `TRIM_INTERVAL`)
- Edge cases in `readAfter` with boundary IDs
- Metadata handling: null metadata, empty metadata map

### DefaultOdysseyStream (86.5%, 7 uncovered lines, 3 conditions)

Cover:
- `publishJson` path
- `delete()` path (distinct from `close()`)
- `replayLast` with count capped at `maxLastN`
- Error handling in `createSubscription`

### PostgresOdysseyStreamNotifier (86.6%, 10 uncovered lines, 3 conditions)

Cover:
- Reconnect path: connection lost, reconnect succeeds
- `dispatchNotification` with malformed payload
- `stop()` while listener is actively polling
- `closeListenConnection` when connection is already null

### CassandraEventLogAutoConfiguration (86.7%, 2 uncovered lines)

Cover:
- The `autoCreateSchema = false` path (schema NOT created)
- Error path: schema creation fails

### SnsOdysseyStreamNotifier (89.8%, 10 uncovered lines, 11 conditions)

Cover:
- `parseAndDispatch` with malformed message body
- `extractSnsMessage` edge cases (no "Message" key, malformed JSON)
- `stop()` cleanup: unsubscribe failure, queue deletion failure
- Poll loop error handling path

### Files above 90% — cover remaining gaps

**`DynamoDbOdysseyEventLog`** (92.8%) — cover remaining 2 lines, 8 conditions
**`RedisOdysseyEventLog`** (93.1%) — cover remaining 1 line, 4 conditions  
**`RabbitMqOdysseyStreamNotifier`** (93.8%) — cover remaining 1 line, 2 conditions
**`StreamSubscriber`** (94.1%) — cover remaining 2 lines
**`CassandraOdysseyEventLog`** (96.7%) — cover remaining 2 conditions
**`RedisOdysseyStreamNotifier`** (96.7%) — cover remaining 1 line
**`MongoOdysseyEventLog`** (97.0%) — cover remaining 2 conditions
**`NatsOdysseyStreamNotifier`** (97.1%) — cover remaining 1 condition
**`StreamWriter`** (97.2%) — cover remaining 1 condition
**`InMemoryOdysseyEventLog`** (98.1%) — cover remaining 1 condition

## Acceptance criteria

- [ ] All files listed above reach 95%+ line coverage (100% where achievable)
- [ ] All uncovered conditions covered where possible
- [ ] Tests use Mockito for mocking, `ApplicationContextRunner` for auto-config
- [ ] Integration tests use Testcontainers and are named `*IT`
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- For auto-configuration tests at 50%: the issue is likely that no test exercises
  the bean creation with a real or mocked infrastructure dependency. Use
  `ApplicationContextRunner` with mocked beans (e.g., mock `Connection` for NATS,
  mock `SnsClient`/`SqsClient` for SNS).
- For `NatsOdysseyEventLog` at 78%: this is likely error handling and edge cases
  in the JetStream API. Mock the JetStream client and simulate failures.
- Run `./mvnw -Pci verify` locally to generate JaCoCo reports and identify
  exactly which lines are uncovered before writing tests.
