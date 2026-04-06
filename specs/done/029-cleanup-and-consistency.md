# Cleanup and consistency pass across all modules

## What to build

A focused cleanup pass across the entire codebase to enforce consistency, fix issues
introduced during rapid module development, and add the `publishJson` API.

### 1. Add `publishJson` to OdysseyStream

Add Jackson 3 (`tools.jackson.databind`) as a dependency in `odyssey-core` and update
the public API:

```java
public interface OdysseyStream {
    String publishRaw(String eventType, String payload);
    String publishJson(String eventType, Object payload);
    // ... rest of the API unchanged
}
```

- Rename the existing `publish` method to `publishRaw`
- Add `publishJson` which serializes the payload via Jackson `ObjectMapper`
- `DefaultOdysseyStream` takes an `ObjectMapper` at construction time
- `DefaultOdysseyStreamRegistry` takes an `ObjectMapper` and passes it to streams
- `OdysseyAutoConfiguration` injects the `ObjectMapper` bean into the registry
- Update all existing `publish` call sites (example app, tests) to `publishRaw`

### 2. Extend `AbstractOdysseyEventLog` everywhere

Ensure ALL event log implementations extend `AbstractOdysseyEventLog` rather than
implementing `OdysseyEventLog` directly. Check:
- `PostgresOdysseyEventLog`
- `CassandraOdysseyEventLog`
- `DynamoDbOdysseyEventLog`
- `MongoOdysseyEventLog` (if it exists)
- `RabbitMqOdysseyEventLog` (if it exists)
- `NatsOdysseyEventLog` (if it exists)

### 3. Magic numbers → properties or constants

Scan for hardcoded values that should be configurable or at least named constants:
- SNS notifier: `MESSAGE_RETENTION_PERIOD` "300" should be a property
  (`odyssey.notifier.sns.sqs-message-retention-seconds=300`)
- Any other hardcoded timeouts, sizes, or retry counts

### 4. Testcontainers 2.x compliance

Ensure ALL integration tests use:
- `org.testcontainers.<db>.XxxContainer` (not `org.testcontainers.containers.XxxContainer`)
- Non-generic container types (no `<?>` or `<>`)
- `testcontainers-*` artifact names in POMs
- `*IT` naming convention (Failsafe, not Surefire)

### 5. Record properties pattern

Ensure ALL `@ConfigurationProperties` classes are records with:
- Defaults in `*-defaults.properties` files
- `@PropertySource` on the auto-configuration class
- Record accessors (`foo()` not `getFoo()`) in all call sites

### 6. License headers

Run `./mvnw -Plicense license:format` to apply Apache 2.0 headers to any new files
Ralph created.

### 7. No `@ConditionalOnProperty`

Remove any `@ConditionalOnProperty` from auto-configuration classes. Use
`@ConditionalOnClass` only.

## Acceptance criteria

- [ ] `OdysseyStream.publishRaw` and `OdysseyStream.publishJson` exist
- [ ] Existing `publish` method renamed to `publishRaw` across all call sites
- [ ] `publishJson` serializes via Jackson `ObjectMapper`
- [ ] All event log implementations extend `AbstractOdysseyEventLog`
- [ ] No magic numbers — configurable via properties or named constants
- [ ] All Testcontainers tests use 2.x classes, non-generic, `*IT` naming
- [ ] All properties classes are records with defaults files
- [ ] No `@ConditionalOnProperty` on any auto-configuration
- [ ] License headers on all files
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes
- [ ] `./mvnw -Plicense license:check` passes

## Implementation notes

- Jackson 3 uses `tools.jackson.databind.ObjectMapper` — not `com.fasterxml.jackson`.
  Spring Boot 4.x auto-configures this.
- The `ObjectMapper` should be a required dependency in core, not optional. Every
  Spring Boot app has Jackson.
- When renaming `publish` to `publishRaw`, update the PRD examples too.
