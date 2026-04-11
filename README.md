# Odyssey

[![CI](https://github.com/jwcarman/odyssey/actions/workflows/maven.yml/badge.svg)](https://github.com/jwcarman/odyssey/actions/workflows/maven.yml)
[![CodeQL](https://github.com/jwcarman/odyssey/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/jwcarman/odyssey/actions/workflows/github-code-scanning/codeql)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/dynamic/xml?url=https://raw.githubusercontent.com/jwcarman/odyssey/main/pom.xml&query=//*[local-name()='java.version']/text()&label=Java&color=orange)](https://openjdk.org/)
[![Maven Central](https://img.shields.io/maven-central/v/org.jwcarman.odyssey/odyssey)](https://central.sonatype.com/artifact/org.jwcarman.odyssey/odyssey)

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

### 1. Add the dependencies

Two dependencies: `odyssey` itself, plus one of [Substrate](https://github.com/jwcarman/substrate)'s
platform modules for the backend you want. Each Substrate module auto-configures a
`JournalFactory` bean, which Odyssey picks up automatically.

**Redis** (most common):
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

Swap `substrate-redis` for whichever backend you want:

| Backend | Substrate artifact |
|---------|--------------------|
| Redis | `substrate-redis` |
| PostgreSQL | `substrate-postgresql` |
| Hazelcast | `substrate-hazelcast` |
| NATS | `substrate-nats` |
| Cassandra | `substrate-cassandra` |
| MongoDB | `substrate-mongodb` |
| DynamoDB | `substrate-dynamodb` |
| RabbitMQ | `substrate-rabbitmq` |

See the [Substrate documentation](https://github.com/jwcarman/substrate) for per-backend
configuration properties (host, credentials, etc.) and for instructions on disabling
individual Substrate primitives you don't need.

### 2. Use it

```java
@RestController
public class OrderStreamController {

    private final Odyssey odyssey;
    private final OdysseyPublisher<OrderEvent> orderStream;

    public OrderStreamController(Odyssey odyssey) {
        this.odyssey = odyssey;
        // Long-lived publisher: one per app lifetime, not one per request.
        this.orderStream = odyssey.publisher("orders", OrderEvent.class);
    }

    @PostMapping("/orders")
    public OrderResponse createOrder(@RequestBody CreateOrder cmd) {
        Order order = orderService.create(cmd);
        orderStream.publish("order.created", OrderEvent.created(order));
        return OrderResponse.from(order);
    }

    @GetMapping("/streams/orders")
    public SseEmitter streamOrders(
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return lastEventId != null
            ? odyssey.resume("orders", OrderEvent.class, lastEventId,
                cfg -> cfg.timeout(Duration.ofMinutes(30)))
            : odyssey.subscribe("orders", OrderEvent.class,
                cfg -> cfg.timeout(Duration.ofMinutes(30)));
    }
}
```

That's it. No boilerplate, no thread management, no Redis commands. Odyssey handles
subscriber coordination, keep-alive heartbeats, reconnect replay, and cleanup.

## Publishing

Publishers are typed. Odyssey handles JSON serialization via its own `ObjectMapper`, so the
backend journal only ever sees an already-serialized byte payload.

```java
// Long-lived publisher -- hold it for the lifetime of the app.
var pub = odyssey.publisher("user:" + userId, OrderEvent.class);
pub.publish("order.shipped", new OrderEvent(orderId, "shipped"));

// Without an event type (SSE event: field omitted -- useful for MCP)
pub.publish(new OrderEvent(orderId, "shipped"));

// Short-lived publisher -- create per request, finalize with complete() when done so
// late-joining subscribers see a terminal Completed state rather than an idle stream.
String streamName = UUID.randomUUID().toString();
var pub = odyssey.publisher(streamName, TaskProgress.class,
    cfg -> cfg.ttl(TtlPolicies.EPHEMERAL));
try {
    pub.publish("progress", new TaskProgress(25));
    pub.publish("progress", new TaskProgress(50));
    pub.publish("done",     new TaskProgress(100));
} finally {
    pub.complete();
}
```

`OdysseyPublisher` is intentionally **not** `AutoCloseable` -- there are no local resources
to release on GC, and making it `AutoCloseable` would encourage try-with-resources which
terminates the underlying stream as a side effect. Explicit `complete()` is the only way
to finalize a stream.

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

## TTL Policies

Odyssey is deliberately unopinionated about stream lifetimes. There is one
`Odyssey.publisher(name, type)` method plus a customizer overload, and exactly one
default `TtlPolicy` configured via `odyssey.default-ttl.*`. If you want
"ephemeral / channel / broadcast" tiering, define your own `TtlPolicy` constants in your
app and pass them via the per-call customizer:

```java
public final class TtlPolicies {
    public static final TtlPolicy EPHEMERAL =
        new TtlPolicy(Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofMinutes(5));
    public static final TtlPolicy CHANNEL =
        new TtlPolicy(Duration.ofHours(1), Duration.ofHours(1), Duration.ofHours(1));
    public static final TtlPolicy BROADCAST =
        new TtlPolicy(Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(24));
}

// Long-lived broadcast
var pub = odyssey.publisher("announcements", Announcement.class,
    cfg -> cfg.ttl(TtlPolicies.BROADCAST));

// Per-request ephemeral
var pub = odyssey.publisher(UUID.randomUUID().toString(), TaskProgress.class,
    cfg -> cfg.ttl(TtlPolicies.EPHEMERAL));
```

Each `TtlPolicy` has three fields: `inactivityTtl` (journal auto-expires if no appends for
this long), `entryTtl` (per-entry TTL applied on every `publish`), and `retentionTtl`
(how long the journal stays readable after `complete()` is called). They don't have to
match -- a broadcast stream might want short `entryTtl` but long `retentionTtl` so
late-joining subscribers can still see recent history after `complete()`.

The three setters on `PublisherConfig` can also be called individually for partial
overrides:

```java
odyssey.publisher("orders", OrderEvent.class, cfg -> cfg.retentionTtl(Duration.ofHours(24)));
```

## Customizers

Configuration uses customizer lambdas applied per call. There is no global customizer bean
mechanism — app-wide defaults come from `odyssey.*` properties, and any cross-cutting logic
you want at every call site should live in a helper method you call explicitly.

```java
// Per-call publisher customizer
odyssey.publisher("orders", OrderEvent.class, cfg -> {
    cfg.inactivityTtl(Duration.ofHours(2));
    cfg.entryTtl(Duration.ofHours(2));
});

// Per-call subscriber customizer
odyssey.subscribe("orders", OrderEvent.class, cfg -> {
    cfg.timeout(Duration.ofMinutes(30));
    cfg.keepAliveInterval(Duration.ofSeconds(15));
    cfg.onCompleted(() -> log.info("Stream completed"));
});
```

The lambda types are `PublisherCustomizer` and `SubscriberCustomizer<T>` — both extend
`Consumer<...>` so any lambda shape you'd write already works.

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

Odyssey only needs a Spring-managed `JournalFactory` bean on the classpath.
[Substrate](https://github.com/jwcarman/substrate) auto-configures one per platform
module; drop any of the following onto the classpath alongside `odyssey` and Odyssey
picks it up automatically. See the table in the [Quick Start](#1-add-the-dependencies)
section for available backends, or the [Substrate
documentation](https://github.com/jwcarman/substrate) for per-backend configuration.

## Configuration

```yaml
odyssey:
  default-ttl:
    inactivity-ttl: 1h   # journal auto-expires if no appends for this long
    entry-ttl: 1h        # per-entry TTL applied on every publish
    retention-ttl: 5m    # how long the journal stays readable after complete()

  sse:
    keep-alive: 30s      # heartbeat / disconnect detection interval
    timeout: 0           # SseEmitter timeout (0 = no timeout)
```

These are just the defaults -- per-stream TTL tiering is the caller's job (see
[TTL Policies](#ttl-policies) above).

Backend-specific properties (storage, connection, etc.) are configured via
[Substrate](https://github.com/jwcarman/substrate). See your backend's documentation.

## Example Application

The [`odyssey-example`](odyssey-example) module is a complete Spring Boot application
with a static HTML page demonstrating three common TTL tiers (ephemeral tasks, per-user
notifications, and broadcast announcements) all built on the single
`odyssey.publisher(name, type, customizer)` entry point. To run it:

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

- Unit tests: `*Test.java` (run by Surefire). `odyssey` is at 100% line, branch,
  method, and class coverage -- please keep it that way when adding new code.
- Backend-specific behavior is exercised in-memory via `InMemoryEndToEndTest`, which
  covers the cross-instance cluster case with a shared `InMemoryJournalSpi`.

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
