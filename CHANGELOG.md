# Changelog

All notable changes to this project will be documented in this file.

## [0.1.0] - 2026-04-07

### Initial Release

Odyssey provides clustered, persistent, resumable Server-Sent Events for Spring Boot,
built on [Substrate](https://github.com/jwcarman/substrate) for pluggable infrastructure.

#### Features

- **Three stream types**: `ephemeral()`, `channel(name)`, `broadcast(name)` — all
  behind a unified `OdysseyStream` API
- **Automatic reconnection**: `resumeAfter(lastEventId)` and `replayLast(count)`
  for seamless client reconnect via SSE `Last-Event-ID`
- **Two publishing styles**: `publishRaw()` for string payloads, `publishJson()` for
  automatic Jackson serialization
- **Nullable event type**: SSE `event:` field is optional — supports MCP Streamable
  HTTP and other protocols that omit it
- **Keep-alive heartbeats**: automatic SSE comments sent on configurable intervals
  to detect disconnects and keep connections alive through proxies
- **Instant connection detection**: SSE `comment("connected")` sent immediately on
  subscribe so clients know the connection is established
- **Stream lifecycle**: `close()` for graceful drain, `delete()` for immediate cleanup
- **Stream lookup**: `registry.stream(streamKey)` for reconnect by key

#### Spring Boot Starters

One dependency for each infrastructure stack:

- `odyssey-redis-spring-boot-starter` — Redis (Streams + Pub/Sub)
- `odyssey-postgresql-spring-boot-starter` — PostgreSQL (table + LISTEN/NOTIFY)
- `odyssey-hazelcast-spring-boot-starter` — Hazelcast (Ringbuffer + ITopic)
- `odyssey-nats-spring-boot-starter` — NATS (JetStream + Core)
- `odyssey-inmemory-spring-boot-starter` — In-memory (no infrastructure)

#### Architecture

- Built on [Substrate](https://github.com/jwcarman/substrate) `Journal` and `Notifier` SPIs
- One writer virtual thread per subscriber — polls `JournalCursor`, sends to `SseEmitter`
- No reader thread in Odyssey — Substrate's cursor handles storage reads internally
- Clean shutdown: `AtomicBoolean` guards ensure idempotent cleanup on disconnect,
  timeout, or error

#### Requirements

- Java 25+
- Spring Boot 4.x
- [Substrate](https://github.com/jwcarman/substrate) 0.1.0+
- [Codec](https://github.com/jwcarman/codec) 0.1.0+ (included in starters)
