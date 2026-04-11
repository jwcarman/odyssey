# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

## [0.7.0] - 2026-04-11

Packaging simplification: Odyssey is now a single published artifact, `odyssey`, that
auto-configures itself on the classpath. The separate per-backend starter modules are
gone — use Substrate's own platform modules directly.

### Breaking changes

**Renamed `odyssey-core` → `odyssey`.**

The Maven coordinates change from `org.jwcarman.odyssey:odyssey-core` to
`org.jwcarman.odyssey:odyssey`. The `-core` suffix implied sibling artifacts (`odyssey-redis`,
etc.) that no longer exist; the base jar is the entire library. Update your dependency:

```xml
<!-- Old (0.6.0) -->
<dependency>
    <groupId>org.jwcarman.odyssey</groupId>
    <artifactId>odyssey-core</artifactId>
    <version>0.6.0</version>
</dependency>

<!-- New (0.7.0) -->
<dependency>
    <groupId>org.jwcarman.odyssey</groupId>
    <artifactId>odyssey</artifactId>
    <version>0.7.0</version>
</dependency>
```

No Java package names change -- `org.jwcarman.odyssey.core.*`,
`org.jwcarman.odyssey.autoconfigure.*`, etc. all stay where they are. Only the Maven
artifactId is different.

**Removed all per-backend starter modules.**

The following artifacts are gone and will not be republished:

- `odyssey-redis-spring-boot-starter`
- `odyssey-postgresql-spring-boot-starter`
- `odyssey-hazelcast-spring-boot-starter`
- `odyssey-nats-spring-boot-starter`
- `odyssey-inmemory-spring-boot-starter`

Each was a pom-only shell aggregating `odyssey-core` + `substrate-<backend>` +
`codec-jackson` + `spring-boot-starter-data-<backend>`. [Substrate's own platform modules
(`substrate-redis`, `substrate-postgresql`, etc.)](https://github.com/jwcarman/substrate)
already provide full Spring Boot auto-configuration — including per-primitive disable
flags — so the Odyssey-side starter became a redundant wrapper. Depend on the Substrate
module directly:

```xml
<dependency>
    <groupId>org.jwcarman.odyssey</groupId>
    <artifactId>odyssey</artifactId>
    <version>0.7.0</version>
</dependency>
<dependency>
    <groupId>org.jwcarman.substrate</groupId>
    <artifactId>substrate-redis</artifactId>
    <version>0.2.0</version>
</dependency>
```

The release surface drops from 6 artifacts (`odyssey-core` + 5 starters) to 1 (`odyssey`).

### Documentation

- README Quick Start rewritten around the two-dependency pattern with a per-backend
  table pointing at Substrate artifacts.
- `OdysseyAutoConfiguration` javadoc updated to reference the `odyssey` jar and
  Substrate's platform auto-config modules instead of the now-gone Odyssey starters.

## [0.6.0] - 2026-04-11

This release is a focused simplification of the Odyssey API. The headline: Odyssey does
one thing and one thing only — it makes `SseEmitter` handling ergonomic in Spring Boot.
Everything in the previous API that wasn't in service of that goal has been removed.

> **Note:** 0.5.0 was tagged but never published to Maven Central (the release workflow
> failed in the javadoc stage). 0.6.0 is the first release shipped with this API.

### Breaking changes

**Removed sugared category methods (`ephemeral` / `channel` / `broadcast`).**

Previous releases offered three creation methods that differed only in which default TTL
policy they applied and whether they prefixed the stream name. Both halves of that were
footguns: the prefixes caused round-trip reconnect bugs (name passed in ≠ name stored in
backend), and the three-way split imposed Odyssey's opinions about stream lifetimes on
every user even when those categories didn't fit their domain.

The entire surface is now:

```java
public interface Odyssey {
  <T> OdysseyPublisher<T> publisher(String name, Class<T> type);
  <T> OdysseyPublisher<T> publisher(String name, Class<T> type, PublisherCustomizer customizer);
  <T> SseEmitter subscribe(String name, Class<T> type);
  <T> SseEmitter subscribe(String name, Class<T> type, SubscriberCustomizer<T> customizer);
  <T> SseEmitter resume(String name, Class<T> type, String lastEventId);
  <T> SseEmitter resume(String name, Class<T> type, String lastEventId, SubscriberCustomizer<T> customizer);
  <T> SseEmitter replay(String name, Class<T> type, int count);
  <T> SseEmitter replay(String name, Class<T> type, int count, SubscriberCustomizer<T> customizer);
}
```

`PublisherCustomizer` and `SubscriberCustomizer<T>` are named `@FunctionalInterface` types
that each extend the obvious `Consumer<...>` — lambdas written for prior drafts
(`cfg -> cfg.ttl(...)`) still compile unchanged; the named types exist purely to make the
method signatures self-documenting.

Removed: `ephemeral(...)`, `channel(...)`, `broadcast(...)` in all their overloads.
Removed: the matching `OdysseyProperties.ephemeral` / `.channel` / `.broadcast` nested
properties.

**Removed global `PublisherCustomizer` / `SubscriberCustomizer` bean mechanism.**

Prior drafts wired `ObjectProvider<PublisherCustomizer>` and
`ObjectProvider<SubscriberCustomizer>` through `OdysseyAutoConfiguration`, letting apps
declare `@Bean PublisherCustomizer` for app-wide defaults. That mechanism is gone:

- `OdysseyAutoConfiguration.odyssey(...)` now takes 3 parameters (`JournalFactory`,
  `ObjectMapper`, `OdysseyProperties`) instead of 5; no `ObjectProvider<...>` wiring.
- `DefaultOdyssey` constructor is now 3 args (no customizer lists).
- `PublisherCustomizer` and `SubscriberCustomizer<T>` are still public types, but only
  as the typed parameter shape in per-call method signatures. Declaring them as Spring
  beans does nothing.

Rationale: for publishers, the mechanism was entirely redundant with
`odyssey.default-ttl.*` (TTL is the only thing on `PublisherConfig`). For subscribers,
only the wildcard-typed form (`SubscriberCustomizer<?>`) could be a global bean, which
forced an awkward `Consumer<SubscriberConfig<?>>` definition and kept the per-call type
from being cleanly parameterized. Killing globals lets
`SubscriberCustomizer<T> extends Consumer<SubscriberConfig<T>>` be fully typed with no
wildcards or unchecked casts anywhere.

If you had app-wide customizer logic, wrap it in a helper method you call at every
publisher/subscribe site.

If you want the "ephemeral / channel / broadcast" tiering, define your own `TtlPolicy`
constants in your app and pass them via the per-call customizer:

```java
public final class TtlPolicies {
  public static final TtlPolicy EPHEMERAL = new TtlPolicy(ofMinutes(5), ofMinutes(5), ofMinutes(5));
  public static final TtlPolicy CHANNEL   = new TtlPolicy(ofHours(1),   ofHours(1),   ofHours(1));
  public static final TtlPolicy BROADCAST = new TtlPolicy(ofHours(24),  ofHours(24),  ofHours(24));
}

var pub = odyssey.publisher("orders:" + id, OrderEvent.class, cfg -> cfg.ttl(TtlPolicies.CHANNEL));
```

Ephemeral stream names (e.g. per-request UUIDs) are generated by the caller:

```java
String streamName = UUID.randomUUID().toString();
var pub = odyssey.publisher(streamName, TaskProgress.class, cfg -> cfg.ttl(TtlPolicies.EPHEMERAL));
```

**`OdysseyPublisher` is no longer `AutoCloseable`.**

The previous `close()` / `close(Duration)` methods were destructive — they called
`journal.complete(retentionTtl)`, which terminates the stream for every subscriber. Pairing
that with `AutoCloseable` was a footgun: try-with-resources looks idiomatic, but for
long-lived streams it would terminate the stream after a single publish, forcing every
subscriber into a tight reconnect loop. Publishers hold no local resources — no threads,
no locks, no sockets — so there is nothing to "close" in the Java sense.

Replace:

```java
try (var pub = odyssey.channel("orders", OrderEvent.class)) {   // WRONG for long-lived
    pub.publish("created", new OrderEvent(...));
}
```

With (long-lived):

```java
var pub = odyssey.publisher("orders", OrderEvent.class, cfg -> cfg.ttl(TtlPolicies.CHANNEL));
pub.publish("created", new OrderEvent(...));
// publisher stays open for the lifetime of the app; no close call
```

Or (short-lived / per-request):

```java
var pub = odyssey.publisher(UUID.randomUUID().toString(), TaskProgress.class,
    cfg -> cfg.ttl(TtlPolicies.EPHEMERAL));
try {
    pub.publish("progress", new TaskProgress(25));
    pub.publish("done",     new TaskProgress(100));
} finally {
    pub.complete();    // explicit finalization, uses retention TTL from PublisherConfig
}
```

Specifically:

- `OdysseyPublisher` no longer extends `AutoCloseable`.
- Removed: `close()`, `close(Duration retentionTtl)`.
- Added: `complete()` — finalizes the stream using the retention TTL from
  `PublisherConfig`. No `Duration` override — retention is a stream-level setting
  configured once via `PublisherConfig`.
- `delete()` unchanged.
- `OdysseyPublisher.key()` **renamed to `OdysseyPublisher.name()`**.

**Stream identifiers are flat names — no category prefixes.**

The name you pass to `odyssey.publisher(name, ...)` is the name that lives in the backend
journal, verbatim. Round-trip reconnect via `odyssey.subscribe(pub.name(), ...)` just works
without any prefix mangling. Prior releases prefixed names under the hood
(`"channel:user:alice"`, `"broadcast:announcements"`, `"ephemeral:<UUID>"`) which caused
round-trip bugs when callers tried to reattach.

`DeliveredEvent.streamKey` → renamed to `DeliveredEvent.streamName`, and now carries the
user-supplied name instead of Substrate's internal backend key.

**Wire-level note:** backend journals created by 0.4.x are not readable by 0.5.0 at the
same logical names — the on-disk keys changed from `substrate:journal:channel:user:alice`
to `substrate:journal:user:alice`. There is no migration script; drop and recreate or run
0.4.x alongside 0.6.0 until your old streams have expired.

**`OdysseyProperties` restructured into `defaultTtl` + `sse` nested records.**

Old:
```yaml
odyssey:
  keep-alive-interval: 30s
  sse-timeout: 0
  ephemeral:
    inactivity-ttl: 5m
    entry-ttl: 5m
    retention-ttl: 5m
  channel:
    inactivity-ttl: 1h
    entry-ttl: 1h
    retention-ttl: 1h
  broadcast:
    inactivity-ttl: 24h
    entry-ttl: 24h
    retention-ttl: 24h
```

New:
```yaml
odyssey:
  default-ttl:
    inactivity-ttl: 1h
    entry-ttl: 1h
    retention-ttl: 5m
  sse:
    keep-alive: 30s
    timeout: 0
```

`OdysseyProperties` is now `record OdysseyProperties(TtlPolicy defaultTtl, SseProperties sse) {}`
where `SseProperties` is a new nested record `(Duration timeout, Duration keepAlive)`.
There is exactly one default TTL policy; per-stream TTL tiering is the caller's job.

### Added

- **`TtlPolicy`** public record in `org.jwcarman.odyssey.core` grouping the three TTL
  durations, with `withInactivityTtl` / `withEntryTtl` / `withRetentionTtl` copy-with
  methods. (Renamed from `StreamTtl` during 0.5.0 development — never shipped under the
  old name.)
- **`PublisherConfig.ttl(TtlPolicy)`** default method — applies all three TTL fields from
  a policy record in one call.
- **`SseProperties`** public record `(Duration timeout, Duration keepAlive)` — nested
  inside `OdysseyProperties` under the `odyssey.sse` prefix.

### Fixed

- **Example app `BroadcastController` / `NotifyController` reconnect loop.** The previous
  implementation wrapped each publish in `try-with-resources`, which terminated the
  long-lived stream after every POST and put every subscriber into a rapid reconnect
  loop. Removed the try-with-resources; publishers now stay open for the application
  lifetime.
- **Example app `TaskController`** explicitly calls `pub.complete()` in a `finally` block
  at the end of the background task.
- **Ephemeral stream round-trip bug.** `OdysseyPublisher.name()` now returns exactly the
  name passed to `odyssey.publisher(name, ...)`, not Substrate's internal backend key.
  Previously, `journalFactory.connect(pub.name(), ...)` could land on a different journal
  than the publisher was writing to because the publisher returned a prefixed form.

## [0.4.0] - 2026-04-11

### Breaking changes

**Complete API redesign around typed events, customizers, and producer/consumer split.**
This is a breaking change with no migration path -- journals written by the old API
cannot be read by the new API.

#### Removed types

- `OdysseyStream` -- replaced by `Odyssey` facade + `OdysseyPublisher<T>`
- `OdysseyStreamRegistry` -- replaced by `Odyssey` facade
- `OdysseyEvent` -- replaced by typed domain events (`T`)
- `StreamSubscriberBuilder` -- replaced by `Consumer<SubscriberConfig<T>>` customizers
- `SseEventMapper` (old non-generic) -- replaced by generic `SseEventMapper<T>` with terminal hooks
- `DefaultOdysseyStream` -- replaced by `DefaultOdyssey` + `DefaultOdysseyPublisher<T>`
- `DefaultOdysseyStreamRegistry` -- replaced by `DefaultOdyssey`
- `StreamSubscription` -- replaced by `SseJournalAdapter<T>`

#### Removed methods

- `publishRaw(String)`, `publishRaw(String, String)` -- use `publisher.publish(T)` or `publisher.publish(String, T)` with `String.class`
- `publishJson(Object)`, `publishJson(String, Object)` -- use typed publishers directly

### Added

- **`Odyssey` facade** -- single top-level interface with publisher and subscriber methods
- **`OdysseyPublisher<T>`** -- typed publisher with `AutoCloseable` support
- **`PublisherConfig` / `SubscriberConfig<T>`** -- customizer-based configuration
  following Spring Boot's `RestClientCustomizer` pattern
- **`DeliveredEvent<T>`** -- typed event record delivered to `SseEventMapper`
- **`SseEventMapper<T>`** -- generic mapper with a `terminal(TerminalState)` hook.
  `TerminalState` is a sealed type with `Completed` / `Expired` / `Deleted` / `Errored(Throwable)`
  records. The default terminal implementation returns `Optional.empty()` -- no opinionated
  SSE frames are injected on termination. When a stream terminates via `Errored` and the mapper
  emits no in-band frame, the adapter closes the emitter via `completeWithError(cause)` so
  Spring MVC's error handling fires; otherwise the emitter is closed via `complete()`.
- **`PublisherCustomizer` / `SubscriberCustomizer`** -- global bean-based customizers
- **`createOrConnect` pattern** -- cluster-safe journal provisioning that handles
  `JournalAlreadyExistsException` race conditions
- **Terminal state callbacks** -- `onCompleted`, `onExpired`, `onDeleted`, `onErrored`
  on `SubscriberConfig`

### Changed

- **Substrate dependency bumped to 0.2.0** (released to Maven Central). Odyssey no longer
  depends on any local SNAPSHOT build of Substrate.

### Fixed

- `SseJournalAdapter` close-before-subscribe race: the subscription is now created in a
  static `launch()` factory, then passed as a `final` constructor argument to the adapter
  itself. Callbacks are wired only after the field is assigned, eliminating the
  null-source-during-close window that previously leaked a `BlockingSubscription` when a
  client disconnected before the writer thread ran. `launch()` handles
  `JournalExpiredException` on initial subscribe inline and routes it through the
  terminal-expired path without ever spawning a writer thread.
- `java:S3077` reliability bug: `SseJournalAdapter.source` was a volatile reference type.
  The refactor above makes it a `final` field with no volatile and no null check in
  `close()`.

### Documentation

- Every public API type now has class-level javadoc. Package-level overview in
  `package-info.java` explains the producer/consumer split and customizer-based
  configuration model.
- `README.md` Terminal State Handling section rewritten to describe the current
  empty-by-default behavior and the `TerminalState` sealed type.

### Quality

- **100% test coverage** across `odyssey-core` (1475 NCLOC, 80 unit tests; all
  instructions, branches, lines, methods, and classes fully covered per JaCoCo)
- Zero open SonarCloud issues (0 bugs, 0 vulnerabilities, 0 code smells)
- Reliability, security, and maintainability ratings all **A** (1.0)
- Duplication 0%
- Zero `@SuppressWarnings` annotations in `odyssey-core`
- Quality gate green on SonarCloud

## [0.3.0] - 2026-04-07

### Added

- **`SseEventMapper`** -- interface for custom `OdysseyEvent` → SSE mapping. Lets
  callers override how events are rendered on the wire.
- **`StreamSubscriberBuilder`** -- fluent API for configuring SSE subscriptions.
  Terminal methods: `subscribe()`, `resumeAfter(String)`, `replayLast(int)`. Accessed
  via `OdysseyStream.subscriber()`. Existing convenience methods on `OdysseyStream`
  delegate to the builder with defaults.
- `StreamSubscription` now uses `SseEventMapper` instead of hardcoded SSE building,
  so custom mappers are applied consistently across all subscription paths.

### Fixed

- README architecture diagram updated to reflect the Substrate-based design.

## [0.2.0] - 2026-04-07

### Added

- **Per-type TTL configuration**: `odyssey.ephemeral-ttl`, `odyssey.channel-ttl`,
  `odyssey.broadcast-ttl` control event retention per stream type (defaults: 5m, 1h, 24h)
- **Starters as jars**: convenience starters are now proper jar artifacts -- no
  `<type>pom</type>` needed in dependency declarations
- Comprehensive debug logging across all lifecycle events
- `comment("connected")` sent immediately on subscribe for fast client detection
- CodeQL badge in README

### Fixed

- Subscription race condition: cleanup is wired before writer thread starts
- Exception handling in writer loop simplified (no more `instanceof` check)
- Removed false TTL claims from README -- TTLs are now actually enforced

### Changed

- `SseStreamEventHandler` and `StreamEventHandler` merged into `StreamSubscription`
  for simplicity -- one class handles cursor polling, SSE writing, and cleanup

## [0.1.1] - 2026-04-07

### Fixed

- Starters now use `jar` packaging instead of `pom`

## [0.1.0] - 2026-04-07

### Initial Release

Odyssey provides clustered, persistent, resumable Server-Sent Events for Spring Boot,
built on [Substrate](https://github.com/jwcarman/substrate) for pluggable infrastructure.

#### Features

- **Three stream types**: `ephemeral()`, `channel(name)`, `broadcast(name)` -- all
  behind a unified `OdysseyStream` API
- **Automatic reconnection**: `resumeAfter(lastEventId)` and `replayLast(count)`
  for seamless client reconnect via SSE `Last-Event-ID`
- **Two publishing styles**: `publishRaw()` for string payloads, `publishJson()` for
  automatic Jackson serialization
- **Nullable event type**: SSE `event:` field is optional -- supports MCP Streamable
  HTTP and other protocols that omit it
- **Keep-alive heartbeats**: automatic SSE comments sent on configurable intervals
  to detect disconnects and keep connections alive through proxies
- **Instant connection detection**: SSE `comment("connected")` sent immediately on
  subscribe so clients know the connection is established
- **Stream lifecycle**: `close()` for graceful drain, `delete()` for immediate cleanup
- **Stream lookup**: `registry.stream(streamKey)` for reconnect by key

#### Spring Boot Starters

One dependency for each infrastructure stack:

- `odyssey-redis-spring-boot-starter` -- Redis (Streams + Pub/Sub)
- `odyssey-postgresql-spring-boot-starter` -- PostgreSQL (table + LISTEN/NOTIFY)
- `odyssey-hazelcast-spring-boot-starter` -- Hazelcast (Ringbuffer + ITopic)
- `odyssey-nats-spring-boot-starter` -- NATS (JetStream + Core)
- `odyssey-inmemory-spring-boot-starter` -- In-memory (no infrastructure)

#### Architecture

- Built on [Substrate](https://github.com/jwcarman/substrate) `Journal` and `Notifier` SPIs
- One writer virtual thread per subscriber -- polls `JournalCursor`, sends to `SseEmitter`
- No reader thread in Odyssey -- Substrate's cursor handles storage reads internally
- Clean shutdown: `AtomicBoolean` guards ensure idempotent cleanup on disconnect,
  timeout, or error

#### Requirements

- Java 25+
- Spring Boot 4.x
- [Substrate](https://github.com/jwcarman/substrate) 0.1.0+
- [Codec](https://github.com/jwcarman/codec) 0.1.0+ (included in starters)
