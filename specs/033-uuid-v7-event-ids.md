# Use UUID v7 for event ID generation

## What to build

Replace the custom `generateEventId()` / `formatEventId()` methods duplicated across
MongoDB, DynamoDB, and RabbitMQ event log implementations with UUID v7 generation in
`AbstractOdysseyEventLog`.

The current approach uses `System.currentTimeMillis()` + an `AtomicInteger` sequence,
which produces duplicate IDs in clustered environments (two nodes at the same millisecond
generate identical IDs).

### Add dependency

Add `com.fasterxml.uuid:java-uuid-generator` to `odyssey-core`:

```xml
<dependency>
    <groupId>com.fasterxml.uuid</groupId>
    <artifactId>java-uuid-generator</artifactId>
</dependency>
```

Spring Boot manages the version — do not specify one.

### Add to AbstractOdysseyEventLog

```java
import com.fasterxml.uuid.Generators;

public abstract class AbstractOdysseyEventLog implements OdysseyEventLog {

    protected String generateEventId() {
        return Generators.timeBasedEpochGenerator().generate().toString();
    }

    // ... existing key methods
}
```

UUID v7 is time-ordered (epoch milliseconds in the high bits), lexicographically
sortable, and unique across nodes without coordination.

### Remove duplicated code

Remove from each implementation:
- `private long lastMillis = -1;`
- `private final AtomicInteger sequence = new AtomicInteger(0);`
- `private synchronized String generateEventId() { ... }`
- `private String formatEventId(long millis, int seq) { ... }`

These implementations should now call `generateEventId()` inherited from the abstract
class:
- `MongoOdysseyEventLog`
- `DynamoDbOdysseyEventLog`
- `RabbitMqOdysseyEventLog`
- `NatsOdysseyEventLog` (if it has the same pattern)

### Update readAfter comparisons

UUID v7 strings are lexicographically sortable, so `eventId > lastId` comparisons
still work. Verify that each implementation's `readAfter` query uses string comparison
(`>`, `$gt`, etc.) correctly with UUID format.

## Acceptance criteria

- [ ] `com.fasterxml.uuid:java-uuid-generator` dependency in `odyssey-core`
- [ ] `AbstractOdysseyEventLog.generateEventId()` uses `Generators.timeBasedEpochGenerator()`
- [ ] No `lastMillis`, `sequence`, `formatEventId` code in any event log implementation
- [ ] All implementations use the inherited `generateEventId()` method
- [ ] `readAfter` works correctly with UUID v7 string comparison
- [ ] SonarCloud duplication issue resolved
- [ ] Clustered environments produce unique, ordered IDs
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- UUID v7 format: `01936b6e-5c6a-7xxx-xxxx-xxxxxxxxxxxx` — the first 48 bits are
  epoch millis, so lexicographic sort = chronological sort.
- Redis and PostgreSQL event logs don't need this — Redis generates its own stream
  entry IDs, PostgreSQL uses `BIGSERIAL`. Cassandra uses `TIMEUUID`.
- The `Generators.timeBasedEpochGenerator()` returns a reusable generator instance.
  Consider storing it as a static field on the abstract class for efficiency.
