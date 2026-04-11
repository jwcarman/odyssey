# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

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

### Quality

- **100% test coverage** across `odyssey-core` (1124 instructions, 24 branches, 281 lines,
  96 methods, 14 classes -- all fully covered)
- Zero open SonarCloud issues (0 bugs, 0 vulnerabilities, 0 code smells)
- SonarCloud quality gate green: reliability / security / maintainability ratings all A,
  duplications 0%

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
