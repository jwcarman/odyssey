# Implement odyssey-notifier-hazelcast

## What to build

Create the `odyssey-notifier-hazelcast` module — an `OdysseyStreamNotifier`
implementation backed by Hazelcast `ITopic`.

**Module setup:**
- `artifactId: odyssey-notifier-hazelcast`
- Depends on `odyssey-core`
- Dependencies: `com.hazelcast:hazelcast` (Spring Boot manages the version)

**`HazelcastOdysseyStreamNotifier` implements `OdysseyStreamNotifier`, `SmartLifecycle`:**
- `notify(streamKey, eventId)`: `topic.publish(streamKey + "|" + eventId)`.
  Fire-and-forget. All Hazelcast members receive the message.
- `subscribe(handler)`: store the handler. On `start()`, register a
  `MessageListener` on the topic that parses the payload and calls
  `handler.onNotification(streamKey, eventId)`.
- `start()`: create the `ITopic` and register the listener.
- `stop()`: remove the listener registration.
- `isRunning()`: return whether the listener is registered.

**No dedicated thread needed.** Hazelcast manages listener threads internally.
This is the simplest notifier implementation — no poll loop, no SQS queue
creation, no PSUBSCRIBE thread.

**`HazelcastNotifierAutoConfiguration`:**
- `@AutoConfiguration(before = OdysseyAutoConfiguration.class)`
- `@ConditionalOnClass(HazelcastInstance.class)`
- Creates a `HazelcastOdysseyStreamNotifier` bean using the `HazelcastInstance`
  from the Spring context
- `@PropertySource("classpath:odyssey-notifier-hazelcast-defaults.properties")`
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Properties (record):**
```properties
odyssey.notifier.hazelcast.topic-name=odyssey-notifications
```

**Payload format:**
- `streamKey|eventId` — same delimiter pattern as other notifiers
- Parse on receive: split on first `|`

## Acceptance criteria

- [ ] `odyssey-notifier-hazelcast` module exists with correct POM
- [ ] `HazelcastOdysseyStreamNotifier` implements `OdysseyStreamNotifier` and
      `SmartLifecycle`
- [ ] `notify` publishes to the Hazelcast `ITopic`
- [ ] `subscribe` registers a handler
- [ ] `start` creates the topic listener
- [ ] `stop` removes the topic listener
- [ ] No dedicated listener thread — Hazelcast handles it
- [ ] Properties are a record with defaults file
- [ ] Auto-configuration registers the bean when Hazelcast is on the classpath
- [ ] License headers on all files
- [ ] Tests with embedded Hazelcast (no Testcontainers needed)
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- `ITopic` is Hazelcast's pub/sub. Every member receives every message — true
  fan-out, same as Redis Pub/Sub.
- `topic.addMessageListener()` returns a `UUID` registration ID. Store it so
  `stop()` can call `topic.removeMessageListener(registrationId)`.
- Use `AtomicReference<UUID>` for the registration ID to handle concurrent
  start/stop safely.
- Hazelcast `ITopic` is reliable by default — messages are delivered in order
  and not lost (unlike Redis Pub/Sub which drops if no one is listening).
- For tests: use a programmatic `HazelcastInstance`. No config files needed.
  Remember to `hazelcast.shutdown()` in `@AfterEach` or `@AfterAll`.
- Since both the eventlog and notifier modules depend on `HazelcastInstance`,
  they naturally pair together for a single-infrastructure Hazelcast stack.
