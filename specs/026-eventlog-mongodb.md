# Implement odyssey-eventlog-mongodb

## What to build

Create the `odyssey-eventlog-mongodb` module — an `OdysseyEventLog` implementation
backed by MongoDB.

**Module setup:**
- `artifactId: odyssey-eventlog-mongodb`
- Depends on `odyssey-core`
- Dependencies: `spring-boot-starter-data-mongodb`

**Collection design:**

```json
// Collection: odyssey_events
{
  "_id": ObjectId,
  "streamKey": "odyssey:channel:user:123",
  "eventId": "1712345678901-0",
  "eventType": "notification.changed",
  "payload": "{...}",
  "timestamp": ISODate("2025-04-06T10:00:00Z"),
  "metadata": { "key": "value" },
  "createdAt": ISODate("2025-04-06T10:00:00Z")
}

// Indexes:
// { streamKey: 1, eventId: 1 }  — compound index for cursor-based reads
// { createdAt: 1 }              — TTL index for automatic expiration
```

Use a timestamp-sequence event ID scheme (`<epochMillis>-<sequence>`) for ordering.
MongoDB's `ObjectId` is already time-ordered, but a custom ID gives us compatibility
with other backends' cursor formats.

**TTL handling:**
- MongoDB supports TTL indexes natively: `createIndex({ createdAt: 1 },
  { expireAfterSeconds: <ttl> })`.
- Events are automatically deleted after the configured TTL.
- Different TTLs per stream type: set `createdAt` on insert, use a single TTL index
  with the longest TTL, and handle shorter TTLs via a separate scheduled cleanup or
  by storing a per-document `expireAt` field with a TTL index on that field.

**`MongoOdysseyEventLog` implements `OdysseyEventLog`:**
- `append(streamKey, event)`: generate event ID, `insertOne` into the collection.
  Return the event ID.
- `readAfter(streamKey, lastId)`: `find({ streamKey: ?, eventId: { $gt: ? } })
  .sort({ eventId: 1 })`. Return as `Stream<OdysseyEvent>`.
- `readLast(streamKey, count)`: `find({ streamKey: ? }).sort({ eventId: -1 })
  .limit(count)`. Reverse results. Return as `Stream<OdysseyEvent>`.
- `delete(streamKey)`: `deleteMany({ streamKey: ? })`.
- Trimming: after append, check document count for the stream key. If exceeding
  `maxLen`, delete oldest documents. Or rely on TTL for cleanup.

**`MongoEventLogAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass(MongoClient.class)`
- Creates a `MongoOdysseyEventLog` bean using `MongoTemplate`
- Ensures indexes exist on startup
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Configuration:**
```yaml
odyssey:
  eventlog:
    mongodb:
      collection: odyssey_events
```

## Acceptance criteria

- [ ] `odyssey-eventlog-mongodb` module exists with correct POM
- [ ] `MongoOdysseyEventLog` implements `OdysseyEventLog`
- [ ] `append` inserts a document and returns a monotonic event ID
- [ ] `readAfter` returns events after the given ID in order
- [ ] `readLast` returns last N events in chronological order
- [ ] `delete` removes all documents for a stream key
- [ ] TTL index is configured for automatic expiration
- [ ] Required indexes are created on startup
- [ ] Auto-configuration registers the bean when MongoDB is on the classpath
- [ ] Integration tests with Testcontainers MongoDB
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Use `MongoTemplate` for simplicity — no need for Spring Data repositories.
- Event ID string comparison must maintain order. The `<epochMillis>-<seq>` format
  works if zero-padded (e.g., `1712345678901-0000`). Alternatively, store as separate
  numeric fields and query on those, exposing the composite string as the external ID.
- MongoDB Change Streams could theoretically be used for notifications too
  (`odyssey-notifier-mongodb`), but that's a separate module for later.
- Testcontainers MongoDB: `new MongoDBContainer("mongo:7")`.
