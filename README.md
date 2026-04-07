# Odyssey

[![CI](https://github.com/jwcarman/odyssey/actions/workflows/maven.yml/badge.svg)](https://github.com/jwcarman/odyssey/actions/workflows/maven.yml)
[![CodeQL](https://github.com/jwcarman/odyssey/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/jwcarman/odyssey/actions/workflows/github-code-scanning/codeql)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/dynamic/xml?url=https://raw.githubusercontent.com/jwcarman/odyssey/main/pom.xml&query=//*[local-name()='java.version']/text()&label=Java&color=orange)](https://openjdk.org/)
[![Maven Central](https://img.shields.io/maven-central/v/org.jwcarman.odyssey/odyssey-core)](https://central.sonatype.com/artifact/org.jwcarman.odyssey/odyssey-core)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_odyssey&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_odyssey)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_odyssey&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_odyssey)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_odyssey&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_odyssey)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_odyssey&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=jwcarman_odyssey)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_odyssey&metric=coverage)](https://sonarcloud.io/summary/new_code?id=jwcarman_odyssey)

Clustered, persistent, resumable Server-Sent Events for Spring Boot. Odyssey bridges the
gap between raw `SseEmitter` and production-ready SSE infrastructure that survives node
failures, client reconnects, and horizontal scaling.

## What's in a Name?

The name **Odyssey** is a nod to **SSE** (**S**erver-**S**ent **E**vents) hidden inside
"Ody**SSE**y" -- a long journey of events, reliably delivered.

## Requirements

- Java 25+
- Spring Boot 4.x

## Features

- Zero reactive types -- virtual threads throughout
- Fixed, predictable connection count per node regardless of stream/subscriber count
- Three stream types (ephemeral, channel, broadcast) behind a unified API
- Self-healing via TTL with explicit close supported
- Keep-alive heartbeat on every stream
- Automatic reconnect with replay (`resumeAfter`, `replayLast`)
- Pluggable storage and notification backends
- In-memory fallback for testing and single-node deployments

## Quick Start

### 1. Add the dependency

Pick a starter for your infrastructure:

**Redis** (most common):
```xml
<dependency>
    <groupId>org.jwcarman.odyssey</groupId>
    <artifactId>odyssey-redis-spring-boot-starter</artifactId>
    <version>0.1.0</version>

</dependency>
```

**In-memory** (no infrastructure, great for development):
```xml
<dependency>
    <groupId>org.jwcarman.odyssey</groupId>
    <artifactId>odyssey-inmemory-spring-boot-starter</artifactId>
    <version>0.1.0</version>

</dependency>
```

Other starters: `odyssey-postgresql-spring-boot-starter`,
`odyssey-hazelcast-spring-boot-starter`, `odyssey-nats-spring-boot-starter`.

Each starter includes everything you need — one dependency.

### 2. Use it

```java
@RestController
public class NotificationController {

    private final OdysseyStreamRegistry registry;

    public NotificationController(OdysseyStreamRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(value = "/events/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @PathVariable String userId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        OdysseyStream stream = registry.channel("user:" + userId);
        return lastEventId != null
            ? stream.resumeAfter(lastEventId)
            : stream.subscribe();
    }

    @PostMapping("/notify/{userId}")
    public void notify(@PathVariable String userId, @RequestBody OrderEvent event) {
        registry.channel("user:" + userId).publishJson("order.updated", event);
    }
}
```

That's it. No boilerplate, no thread management, no Redis commands. Odyssey handles
subscriber coordination, keep-alive heartbeats, reconnect replay, and cleanup.

### Publishing

Odyssey supports two publishing styles:

```java
// Raw string payload
stream.publishRaw("event-type", "{\"key\":\"value\"}");

// Java object serialized to JSON automatically
stream.publishJson("order.shipped", orderEvent);

// Without an event type (SSE event: field omitted — useful for MCP)
stream.publishRaw(jsonPayload);
stream.publishJson(someObject);
```

`publishJson` uses Jackson to serialize the object. The SSE `data:` field contains
the JSON string. Clients parse it with `JSON.parse(event.data)`.

## Stream Types

All three stream types use the same API and architecture. The difference is naming
convention and default TTL configuration.

| Type | Factory Method | Use Case | Default TTL |
|------|---------------|----------|-------------|
| Ephemeral | `registry.ephemeral()` | Short-lived request/response (MCP tool calls) | 5 minutes |
| Channel | `registry.channel(name)` | Per-user or per-entity notifications | 1 hour |
| Broadcast | `registry.broadcast(name)` | System-wide announcements | 24 hours |

### Ephemeral Streams

Auto-generated unique key. Ideal for request-scoped exchanges:

```java
OdysseyStream stream = registry.ephemeral();
// stream.getStreamKey() returns the auto-generated key

executorService.submit(() -> {
    stream.publishRaw("progress", "{\"status\":\"running\"}");
    String result = doWork();
    stream.publishRaw("result", result);
    stream.close();
});

return stream.subscribe();
```

### Channel Streams

Named, cached per key. Multiple subscribers on the same name share a stream:

```java
OdysseyStream stream = registry.channel("user:" + userId);
stream.publishRaw("notification.changed", payload);
```

### Broadcast Streams

Same as channel, but semantically for many subscribers on few keys:

```java
OdysseyStream stream = registry.broadcast("announcements");
stream.publishRaw("maintenance", payload);
```

### Reconnecting

Clients reconnect with `Last-Event-ID` to resume where they left off:

```java
stream.resumeAfter(lastEventId);  // replay missed events, then go live
stream.replayLast(10);            // replay last 10 events, then go live
```

### Looking Up Streams by Key

For reconnect scenarios where you need to find an existing stream:

```java
OdysseyStream stream = registry.stream(streamKey);
return stream.resumeAfter(lastEventId);
```

## Architecture

Each subscriber gets two virtual threads and a `BlockingQueue`:

```
[Pub/Sub Notification] → Semaphore → [Reader Thread] → BlockingQueue → [Writer Thread] → SseEmitter
```

- **Reader thread**: wakes on semaphore nudge, reads from the event log, offers events
  to the queue
- **Writer thread**: polls the queue, sends events to the `SseEmitter`, sends keep-alive
  on timeout

The Pub/Sub notification listener does zero I/O -- it just releases semaphores. All
event log reads happen on the reader thread via the shared connection.

## Backend Support

Odyssey uses [Substrate](https://github.com/jwcarman/substrate) for distributed
storage and notifications. Pick a starter that matches your infrastructure:

| Starter | Backend |
|---------|---------|
| `odyssey-redis-spring-boot-starter` | Redis (Streams + Pub/Sub) |
| `odyssey-postgresql-spring-boot-starter` | PostgreSQL (table + LISTEN/NOTIFY) |
| `odyssey-hazelcast-spring-boot-starter` | Hazelcast (Ringbuffer + ITopic) |
| `odyssey-nats-spring-boot-starter` | NATS (JetStream + Core) |
| `odyssey-inmemory-spring-boot-starter` | In-memory (no infrastructure) |

For advanced use cases, you can mix and match Substrate modules directly.
See the [Substrate documentation](https://github.com/jwcarman/substrate) for
details on all available backends (Cassandra, DynamoDB, MongoDB, RabbitMQ, SNS,
and more).

## Configuration

Core properties (all backends):

```yaml
odyssey:
  keep-alive-interval: 30s   # heartbeat interval
  sse-timeout: 0              # SseEmitter timeout (0 = no timeout)
  max-last-n: 500             # cap for replayLast()
```

Each backend module has its own properties under `odyssey.eventlog.<backend>.*` or
`odyssey.notifier.<backend>.*`. See module documentation for details.

## Example Application

The [`odyssey-example`](odyssey-example) module is a complete Spring Boot application
with a static HTML page demonstrating all three stream types. To run it:

```bash
# Start Redis (required for the example)
docker run -d -p 6379:6379 redis:7

# Run the example
./mvnw -pl odyssey-example spring-boot:run
```

Open http://localhost:8080 to interact with broadcast, channel, and ephemeral streams.

## Building

```bash
# Compile and run unit tests
./mvnw test

# Full build including integration tests (requires Docker for Testcontainers)
./mvnw verify

# Apply code formatting
./mvnw spotless:apply

# Apply license headers
./mvnw -Plicense license:format
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Ensure `./mvnw verify` passes (including integration tests)
5. Ensure `./mvnw spotless:check` passes
6. Ensure `./mvnw -Plicense license:check` passes
7. Submit a pull request

### Code Style

This project uses [Google Java Format](https://github.com/google/google-java-format)
enforced by Spotless. Install the
[IntelliJ plugin](https://plugins.jetbrains.com/plugin/8527-google-java-format) for
IDE integration.

### Testing

- Unit tests: `*Test.java` (run by Surefire)
- Integration tests: `*IT.java` (run by Failsafe, requires Docker)
- Testcontainers 2.x for all integration tests

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
