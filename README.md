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
- Typed domain events -- work with your own `T`, not framework types
- Customizer-based configuration following Spring Boot idioms
- Producer/consumer split -- publishers and subscribers are independent
- Rich terminal-state signaling (completed, expired, deleted, errored)
- Automatic reconnect with replay (`resume`, `replay`)
- Pluggable storage and notification backends via Substrate
- In-memory fallback for testing and single-node deployments

## Quick Start

### 1. Add the dependency

Pick a starter for your infrastructure:

**Redis** (most common):
```xml
<dependency>
    <groupId>org.jwcarman.odyssey</groupId>
    <artifactId>odyssey-redis-spring-boot-starter</artifactId>
    <version>0.4.0</version>
</dependency>
```

**In-memory** (no infrastructure, great for development):
```xml
<dependency>
    <groupId>org.jwcarman.odyssey</groupId>
    <artifactId>odyssey-inmemory-spring-boot-starter</artifactId>
    <version>0.4.0</version>
</dependency>
```

Other starters: `odyssey-postgresql-spring-boot-starter`,
`odyssey-hazelcast-spring-boot-starter`, `odyssey-nats-spring-boot-starter`.

Each starter includes everything you need -- one dependency.

### 2. Use it

```java
@RestController
public class OrderStreamController {

    private final Odyssey odyssey;

    public OrderStreamController(Odyssey odyssey) {
        this.odyssey = odyssey;
    }

    @PostMapping("/orders")
    public OrderResponse createOrder(@RequestBody CreateOrder cmd) {
        Order order = orderService.create(cmd);
        try (var pub = odyssey.channel("orders:" + order.id(), OrderEvent.class)) {
            pub.publish("order.created", OrderEvent.created(order));
        }
        return OrderResponse.from(order);
    }

    @GetMapping("/streams/orders/{id}")
    public SseEmitter streamOrder(
            @PathVariable String id,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        String key = "orders:" + id;
        return lastEventId != null
            ? odyssey.resume(key, OrderEvent.class, lastEventId,
                cfg -> cfg.timeout(Duration.ofMinutes(30)))
            : odyssey.subscribe(key, OrderEvent.class,
                cfg -> cfg.timeout(Duration.ofMinutes(30)));
    }
}
```

That's it. No boilerplate, no thread management, no Redis commands. Odyssey handles
subscriber coordination, keep-alive heartbeats, reconnect replay, and cleanup.

## Publishing

Publishers are typed and support try-with-resources:

```java
// Typed publisher -- Odyssey handles JSON serialization via its own ObjectMapper,
// so the backend journal only ever sees an already-serialized byte payload.
try (var pub = odyssey.channel("user:" + userId, OrderEvent.class)) {
    pub.publish("order.shipped", new OrderEvent(orderId, "shipped"));
}

// Without an event type (SSE event: field omitted -- useful for MCP)
pub.publish(new OrderEvent(orderId, "shipped"));
```

## Subscribing

Subscribers get an `SseEmitter` directly -- there is no user-visible subscriber type:

```java
// Live tail from current head
SseEmitter emitter = odyssey.subscribe(key, OrderEvent.class);

// Resume after a known event ID
SseEmitter emitter = odyssey.resume(key, OrderEvent.class, lastEventId);

// Replay last N entries, then continue tailing
SseEmitter emitter = odyssey.replay(key, OrderEvent.class, 10);
```

## Stream Types

All three stream types use the same API. The difference is naming convention and
default TTLs.

| Type | Factory Method | Use Case |
|------|---------------|----------|
| Ephemeral | `odyssey.ephemeral(type)` | Short-lived request/response (MCP tool calls) |
| Channel | `odyssey.channel(name, type)` | Per-user or per-entity notifications |
| Broadcast | `odyssey.broadcast(name, type)` | System-wide announcements |

Each stream type has a configurable TTL that controls how long events are retained:

```yaml
odyssey:
  ephemeral-ttl: 5m    # short-lived request/response
  channel-ttl: 1h      # per-user notifications
  broadcast-ttl: 24h   # system-wide announcements
```

## Customizers

Configuration uses the Spring Boot customizer pattern:

```java
// Per-call customizer
odyssey.channel("orders", OrderEvent.class, cfg -> {
    cfg.inactivityTtl(Duration.ofHours(2));
    cfg.entryTtl(Duration.ofHours(2));
});

// Global customizer bean (applies to all publishers)
@Bean
PublisherCustomizer longRetention() {
    return cfg -> cfg.retentionTtl(Duration.ofHours(1));
}

// Subscriber customizer
odyssey.subscribe(key, OrderEvent.class, cfg -> {
    cfg.timeout(Duration.ofMinutes(30));
    cfg.keepAliveInterval(Duration.ofSeconds(15));
    cfg.onCompleted(() -> log.info("Stream completed"));
});
```

## Terminal State Handling

Substrate's `NextResult` distinguishes four ways a subscription can terminate: `Completed`,
`Expired`, `Deleted`, and `Errored(Throwable)`. Odyssey surfaces each to user code via
`SseEventMapper.terminal(TerminalState)`, which defaults to emitting **no** terminal frame
so vanilla SSE clients see a clean stream followed by a connection close.

For `Errored`, if the mapper emits no in-band frame, Odyssey closes the emitter via
`SseEmitter.completeWithError(cause)` so Spring MVC's error-handling pipeline fires. For
the other terminal variants (or when the mapper does emit a frame), the emitter is closed
normally via `complete()`.

Users who want explicit terminal signaling override `terminal()` and return whatever frame
they want -- there is no Odyssey-branded naming convention imposed on the wire:

```java
SseEventMapper<OrderEvent> mapper = new SseEventMapper<>() {
    @Override
    public SseEmitter.SseEventBuilder map(DeliveredEvent<OrderEvent> event) {
        return SseEmitter.event().id(event.id()).name(event.eventType()).data(event.data());
    }

    @Override
    public Optional<SseEmitter.SseEventBuilder> terminal(TerminalState state) {
        return switch (state) {
            case TerminalState.Completed() -> Optional.of(SseEmitter.event().name("done"));
            case TerminalState.Expired() -> Optional.of(SseEmitter.event().name("expired"));
            case TerminalState.Deleted() -> Optional.of(SseEmitter.event().name("deleted"));
            case TerminalState.Errored(Throwable cause) ->
                Optional.of(SseEmitter.event().name("failed").data(cause.getMessage()));
        };
    }
};
```

Callers can also attach side-effect-only terminal callbacks via the subscriber config:

```java
odyssey.subscribe(key, OrderEvent.class, cfg -> {
    cfg.onCompleted(() -> metrics.count("stream.completed"));
    cfg.onExpired(() -> metrics.count("stream.expired"));
    cfg.onDeleted(() -> metrics.count("stream.deleted"));
    cfg.onErrored(cause -> log.error("stream errored", cause));
});
```

## Architecture

Each subscriber gets one virtual writer thread that polls a
[Substrate](https://github.com/jwcarman/substrate) `BlockingSubscription`:

```
[Substrate: Notifier -> Semaphore -> Reader Thread -> Queue] -> BlockingSubscription.next() -> [Odyssey Writer Thread] -> SseEmitter
```

- **Substrate** handles storage reads, notification listening, and cursor management
  internally -- Odyssey doesn't manage reader threads, semaphores, or queues
- **Writer thread** (Odyssey): polls `subscription.next(keepAliveInterval)`, sends events
  to the `SseEmitter`, sends keep-alive comments on timeout, handles cleanup on
  disconnect

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

```yaml
odyssey:
  keep-alive-interval: 30s   # heartbeat / disconnect detection interval
  sse-timeout: 0              # SseEmitter timeout (0 = no timeout)
  ephemeral-ttl: 5m           # TTL for ephemeral stream events
  channel-ttl: 1h             # TTL for channel stream events
  broadcast-ttl: 24h          # TTL for broadcast stream events
```

Backend-specific properties (storage, connection, etc.) are configured via
[Substrate](https://github.com/jwcarman/substrate). See your backend's documentation.

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

# Full build with coverage report (JaCoCo)
./mvnw -Pci verify

# Apply code formatting
./mvnw spotless:apply
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Ensure `./mvnw verify` passes
5. Ensure `./mvnw spotless:check` passes
6. Submit a pull request

### Code Style

This project uses [Google Java Format](https://github.com/google/google-java-format)
enforced by Spotless. Install the
[IntelliJ plugin](https://plugins.jetbrains.com/plugin/8527-google-java-format) for
IDE integration.

### Testing

- Unit tests: `*Test.java` (run by Surefire). `odyssey-core` is at 100% line, branch,
  method, and class coverage -- please keep it that way when adding new code.
- Backend-specific behavior is exercised in-memory via `InMemoryEndToEndTest`, which
  covers the cross-instance cluster case with a shared `InMemoryJournalSpi`.

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
