# Migrate Odyssey to use Substrate

## What to build

Replace Odyssey's own SPI implementations with Substrate's Journal and Notifier.
Odyssey becomes a thin SSE layer on top of Substrate — no backend modules, no
event log SPI, no notifier SPI. All infrastructure lives in Substrate.

### Phase 1: Add Substrate dependency

Add `substrate-core` and `codec-jackson` as dependencies of `odyssey-core`. Substrate
must be installed locally first (`mvn install` in the substrate project).

```xml
<dependency>
    <groupId>org.jwcarman.substrate</groupId>
    <artifactId>substrate-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.jwcarman.codec</groupId>
    <artifactId>codec-jackson</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

With `codec-jackson`, the Journal is strongly typed. Odyssey uses
`Journal<OdysseyEvent>` directly — no manual JSON serialization.

### Phase 2: Rewrite the engine to use Substrate

**`OdysseyStream` interface** — unchanged. The public API stays the same.

**`OdysseyStreamRegistry` interface** — unchanged.

**`DefaultOdysseyStreamRegistry`** — rewrite to use `JournalFactory`:

```java
public class DefaultOdysseyStreamRegistry implements OdysseyStreamRegistry {
    private final JournalFactory journalFactory;
    private final long keepAliveInterval;
    private final long defaultSseTimeout;

    public OdysseyStream ephemeral() {
        Journal<OdysseyEvent> journal = journalFactory.create(
            "ephemeral:" + UUID.randomUUID(), OdysseyEvent.class);
        return new DefaultOdysseyStream(journal, keepAliveInterval, defaultSseTimeout);
    }

    public OdysseyStream channel(String name) {
        return cache.computeIfAbsent("channel:" + name, key -> {
            Journal<OdysseyEvent> journal = journalFactory.create(key, OdysseyEvent.class);
            return new DefaultOdysseyStream(journal, keepAliveInterval, defaultSseTimeout);
        });
    }
    // ... broadcast and stream(key) similar
}
```

**`DefaultOdysseyStream`** — rewrite to use `Journal<OdysseyEvent>`:

```java
public class DefaultOdysseyStream implements OdysseyStream {
    private final Journal<OdysseyEvent> journal;

    public String publishRaw(String eventType, String payload) {
        OdysseyEvent event = OdysseyEvent.builder()
            .eventType(eventType)
            .payload(payload)
            .timestamp(Instant.now())
            .build();
        return journal.append(event);  // Substrate serializes via codec
    }

    public SseEmitter subscribe() {
        JournalCursor<OdysseyEvent> cursor = journal.read();
        // Start writer thread that polls cursor
    }

    public SseEmitter resumeAfter(String lastEventId) {
        JournalCursor<OdysseyEvent> cursor = journal.readAfter(lastEventId);
        // Start writer thread
    }

    public SseEmitter replayLast(int count) {
        JournalCursor<OdysseyEvent> cursor = journal.readLast(count);
        // Start writer thread
    }

    public void close() {
        journal.complete();
    }

    public void delete() {
        journal.delete();
    }
}
```

**Writer thread** — simplified. No reader thread needed (Substrate handles that
inside the `JournalCursor`). The writer just polls the cursor. No deserialization
needed — the cursor returns `JournalEntry<OdysseyEvent>` already typed:

```java
void writerLoop(JournalCursor<OdysseyEvent> cursor, StreamEventHandler handler) {
    while (cursor.isOpen()) {
        Optional<JournalEntry<OdysseyEvent>> entry = cursor.poll(keepAliveInterval);
        if (entry.isPresent()) {
            handler.onEvent(entry.get().data());  // already an OdysseyEvent
        } else {
            handler.onKeepAlive();
        }
    }
    handler.onComplete();
}
```

**`OdysseyEvent`** — keep as the Odyssey domain object. Serialized to/from JSON
when stored in the Journal. The Journal stores `String` (JSON), not `OdysseyEvent`
directly.

**`StreamEventHandler` / `SseStreamEventHandler`** — unchanged. Still Odyssey's
concern.

### Phase 3: Remove Odyssey's own SPI and backend modules

Delete these from Odyssey entirely:
- `org.jwcarman.odyssey.spi` package (OdysseyEventLog, OdysseyStreamNotifier,
  AbstractOdysseyEventLog, NotificationHandler)
- `org.jwcarman.odyssey.memory` package (InMemoryOdysseyEventLog,
  InMemoryOdysseyStreamNotifier)
- `StreamReader` class (replaced by JournalCursor's internal reader)
- `StreamSubscriberGroup` (notifications handled by JournalCursor's notifier
  subscription)
- `StreamSubscriber` (simplified — just a writer thread + cursor)

Delete these modules entirely:
- `odyssey-eventlog-redis`
- `odyssey-eventlog-postgresql`
- `odyssey-eventlog-cassandra`
- `odyssey-eventlog-dynamodb`
- `odyssey-eventlog-mongodb`
- `odyssey-eventlog-rabbitmq`
- `odyssey-eventlog-nats`
- `odyssey-eventlog-hazelcast`
- `odyssey-notifier-redis`
- `odyssey-notifier-postgresql`
- `odyssey-notifier-nats`
- `odyssey-notifier-sns`
- `odyssey-notifier-rabbitmq`
- `odyssey-notifier-hazelcast`

### Phase 4: Update auto-configuration

`OdysseyAutoConfiguration` no longer creates event log or notifier beans. It just
needs a `JournalFactory` bean (provided by Substrate's auto-config):

```java
@AutoConfiguration
@EnableConfigurationProperties(OdysseyProperties.class)
public class OdysseyAutoConfiguration {

    @Bean
    public DefaultOdysseyStreamRegistry odysseyStreamRegistry(
            JournalFactory journalFactory,
            ObjectMapper objectMapper,
            OdysseyProperties properties) {
        return new DefaultOdysseyStreamRegistry(
            journalFactory,
            objectMapper,
            properties.keepAliveInterval().toMillis(),
            properties.sseTimeout().toMillis());
    }
}
```

No in-memory fallback warnings — that's Substrate's job now.

### Phase 5: Update BOM

`odyssey-bom` shrinks to just:
- `odyssey-core`

All backend modules are in `substrate-bom`.

### Phase 6: Update example app

The example app's dependencies change:

```xml
<!-- Odyssey (SSE engine) -->
<dependency>
    <groupId>org.jwcarman.odyssey</groupId>
    <artifactId>odyssey-core</artifactId>
</dependency>

<!-- Substrate (infrastructure) -->
<dependency>
    <groupId>org.jwcarman.substrate</groupId>
    <artifactId>substrate-journal-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.jwcarman.substrate</groupId>
    <artifactId>substrate-notifier-redis</artifactId>
</dependency>
```

### Phase 7: Update tests

- Rewrite engine tests to use Substrate's `InMemoryJournalSpi` and `InMemoryNotifier`
  (or mock `JournalFactory` / `Journal<String>`)
- Delete all backend-specific tests (they live in Substrate now)
- Keep SSE-specific tests (SseStreamEventHandler, writer thread behavior)

### Phase 8: Clean up

- Remove `OdysseyProperties.maxLastN` — that's a Journal/Substrate concern now
- Remove `streamPrefix` references — key naming is Substrate's job
- Update PRD and README to reflect the new architecture
- Update parent POM to remove backend modules from `<modules>`
- Run `./mvnw -Plicense license:format` for headers on new/changed files

## Acceptance criteria

- [ ] `odyssey-core` depends on `substrate-core`
- [ ] No Odyssey SPI interfaces remain (OdysseyEventLog, OdysseyStreamNotifier)
- [ ] No backend modules remain in Odyssey
- [ ] `DefaultOdysseyStreamRegistry` uses `JournalFactory`
- [ ] `DefaultOdysseyStream` uses `Journal<String>` and `JournalCursor<String>`
- [ ] No `StreamReader` class — cursor handles reading
- [ ] Writer thread polls `JournalCursor` directly
- [ ] `OdysseyStream` public API unchanged
- [ ] `StreamEventHandler` / `SseStreamEventHandler` unchanged
- [ ] Example app works with Substrate Redis modules
- [ ] All tests pass
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Substrate must be `mvn install`ed locally before Odyssey can build.
- The `JournalCursor` already has the reader thread + semaphore + queue pattern.
  Odyssey's writer thread just polls `cursor.poll(timeout)` — no semaphore,
  no reader thread, no `StreamSubscriberGroup`.
- With `codec-jackson`, the Journal is `Journal<OdysseyEvent>`. Substrate handles
  serialization/deserialization via the Jackson codec. No manual JSON in Odyssey.
- The `maxLastN` cap was an Odyssey concern. With Substrate, the Journal
  implementation handles its own capacity limits. Remove `maxLastN` from
  `OdysseyProperties`.
- `close()` maps to `journal.complete()`. `delete()` maps to `journal.delete()`.
- Substrate's `JournalCursor.poll()` returns `Optional.empty()` on timeout
  (keep-alive) and closes the cursor when the journal is completed (POISON
  equivalent).
