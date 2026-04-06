# Implement odyssey-eventlog-dynamodb

## What to build

Create the `odyssey-eventlog-dynamodb` module — an `OdysseyEventLog` implementation
backed by Amazon DynamoDB.

**Module setup:**
- `artifactId: odyssey-eventlog-dynamodb`
- Depends on `odyssey-core`
- Dependencies: `software.amazon.awssdk:dynamodb` (AWS SDK v2)

**Table design:**

```
Table: odyssey_events
  Partition key: stream_key (S)
  Sort key:      event_id (S)     — zero-padded monotonic counter: "00000000000001"

Attributes:
  event_type  (S)
  payload     (S)
  timestamp   (S)     — ISO-8601
  metadata    (M)     — DynamoDB map
  ttl         (N)     — Unix epoch seconds for DynamoDB TTL

GSI: none needed (all access is by partition key)
```

Use a zero-padded counter string as the sort key — lexicographic ordering matches
numeric ordering. Generate via an atomic counter (DynamoDB `UpdateItem` with
`ADD counter 1` on a separate counters table, or use a timestamp-based scheme similar
to Redis Stream IDs: `<epochMillis>-<sequence>`).

**`DynamoDbOdysseyEventLog` implements `OdysseyEventLog`:**
- `append(streamKey, event)`: generate event ID, `PutItem` to the table. Set `ttl`
  attribute to `now + configured TTL` for DynamoDB's built-in TTL expiration. Return
  the event ID.
- `readAfter(streamKey, lastId)`: `Query` with `stream_key = ? AND event_id > ?`,
  `ScanIndexForward: true`. Return as `Stream<OdysseyEvent>` (paginate via DynamoDB's
  `LastEvaluatedKey`).
- `readLast(streamKey, count)`: `Query` with `stream_key = ?`,
  `ScanIndexForward: false`, `Limit: count`. Reverse results. Return as
  `Stream<OdysseyEvent>`.
- `delete(streamKey)`: `Query` all items for the partition key, then `BatchWriteItem`
  to delete them (DynamoDB doesn't support partition-level deletes).

**`DynamoDbEventLogAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass(DynamoDbClient.class)`
- Creates a `DynamoDbOdysseyEventLog` bean using the `DynamoDbClient`
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Configuration:**
```yaml
odyssey:
  eventlog:
    dynamodb:
      table-name: odyssey_events
      auto-create-table: false
```

## Acceptance criteria

- [ ] `odyssey-eventlog-dynamodb` module exists with correct POM
- [ ] `DynamoDbOdysseyEventLog` implements `OdysseyEventLog`
- [ ] `append` stores event and returns a monotonically increasing ID
- [ ] `append` sets DynamoDB TTL attribute for automatic expiration
- [ ] `readAfter` returns events after the given ID in order
- [ ] `readLast` returns last N events in chronological order
- [ ] `delete` removes all events for a stream key
- [ ] Auto-configuration registers the bean when DynamoDB client is on the classpath
- [ ] Integration tests with Testcontainers LocalStack (DynamoDB)
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Use AWS SDK v2 (`software.amazon.awssdk:dynamodb`), not v1.
- DynamoDB TTL is eventual — items may persist up to 48 hours past expiry. This is fine
  for our use case; it's a cleanup mechanism, not a hard guarantee.
- For the event ID counter, a timestamp-based scheme (`<epochMillis>-<seq>`) avoids the
  need for a separate counters table. Use `System.currentTimeMillis()` + an
  `AtomicInteger` sequence within the same millisecond, zero-padded.
- Testcontainers LocalStack: `new LocalStackContainer(DockerImageName.parse("localstack/localstack"))
  .withServices(LocalStackContainer.Service.DYNAMODB)`.
- `delete` is expensive in DynamoDB (scan + batch delete). Document this trade-off.
  For most use cases, TTL handles cleanup and `delete` is rare.
