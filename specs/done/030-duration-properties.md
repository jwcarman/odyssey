# Use Duration for all time-based properties

## What to build

Replace all time-based properties that use raw numeric types (seconds, milliseconds)
with `java.time.Duration`. The consumer configures in human-readable format (`5m`, `1h`,
`24h`). Each implementation converts to whatever its infrastructure requires internally.

### Properties to convert

Scan all `@ConfigurationProperties` records and their defaults files for any field that
represents a time period. Convert from numeric types to `Duration`. Examples:

- `defaultTtlSeconds` (long) → `defaultTtl` (Duration)
- `ttlSeconds` (long) → `ttl` (Duration)
- `pollTimeoutMillis` (long) → `pollTimeout` (Duration)
- `sqs-message-retention-seconds` → `sqsMessageRetention` (Duration)
- Any other `*Seconds`, `*Millis`, `*Ms` fields

### Defaults files

Update the defaults properties files to use Duration format:
```properties
# Before
odyssey.eventlog.cassandra.default-ttl-seconds=86400
# After
odyssey.eventlog.cassandra.default-ttl=24h
```

Spring Boot automatically parses Duration from strings like `5m`, `1h`, `24h`, `30s`.

### Implementation conversion

Each implementation converts Duration to whatever its infrastructure needs:
- Redis: `properties.ephemeralTtl().toSeconds()` (already does this)
- Cassandra: `(int) properties.defaultTtl().toSeconds()`
- DynamoDB: `Instant.now().plus(properties.ttl()).getEpochSecond()`
- SNS/SQS: `String.valueOf(properties.sqsMessageRetention().toSeconds())`
- PostgreSQL notifier: `properties.pollTimeout().toMillis()`

## Acceptance criteria

- [ ] All time-based properties use `Duration` type
- [ ] No `*Seconds`, `*Millis`, `*Ms` suffixes on property names
- [ ] Defaults files use Duration format (`5m`, `1h`, `30s`)
- [ ] Implementation classes convert Duration internally
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Spring Boot's `@ConfigurationProperties` handles Duration binding automatically.
  Values like `5m`, `30s`, `1h`, `24h` are parsed by `DurationStyle`.
- The Redis event log properties already use Duration for TTLs — use that as the
  reference pattern.
