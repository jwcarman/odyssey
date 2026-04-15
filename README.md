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
- Single `OdysseyStream<T>` handle for publish, subscribe, resume, replay, complete, delete
- Broker-style semantics: streams are implicitly created on first reference
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

    private final OdysseyStream<OrderEvent> orders;

    public OrderStreamController(Odyssey odyssey) {
        // Long-lived handle: one per app lifetime, not one per request.
        this.orders = odyssey.stream("orders", OrderEvent.class);
    }

    @PostMapping("/orders")
    public OrderResponse createOrder(@RequestBody CreateOrder cmd) {
        Order order = orderService.create(cmd);
        orders.publish("order.created", OrderEvent.created(order));
        return OrderResponse.from(order);
    }

    @GetMapping("/streams/orders")
    public SseEmitter streamOrders(
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return lastEventId != null
            ? orders.resume(lastEventId, cfg -> cfg.timeout(Duration.ofMinutes(30)))
            : orders.subscribe(cfg -> cfg.timeout(Duration.ofMinutes(30)));
    }
}
```

That's it. No boilerplate, no thread management, no Redis commands. Odyssey handles
subscriber coordination, keep-alive heartbeats, reconnect replay, and cleanup.

## The `OdysseyStream<T>` handle

`Odyssey.stream(name, type, ttl)` returns a typed handle that carries the stream's name,
element type, and TTL policy. Every operation on the stream is a method on the handle:

```java
OdysseyStream<OrderEvent> s = odyssey.stream("orders", OrderEvent.class, BROADCAST);

// Publish
String id = s.publish("order.shipped", new OrderEvent(orderId, "shipped"));
s.publish(new OrderEvent(orderId, "shipped"));      // no event type (useful for MCP)

// Subscribe -- three starting positions, encoded in the method name
SseEmitter tail   = s.subscribe();
SseEmitter after  = s.resume(lastEventId);
SseEmitter replay = s.replay(10);

// Finalize
s.complete();   // no more appends; entries readable for retentionTtl
s.delete();     // entries gone immediately; subscribers receive Deleted terminal state

String name = s.name();   // round-trips back to the same handle
```

Streams are **get-or-create**. The first caller for a given name in the backend creates
the stream with the supplied TTL; later callers (this process or any other) silently
adopt the existing stream and their TTL argument is ignored. This matches Kafka,
ActiveMQ, and NATS JetStream conventions: producers and consumers don't configure
retention on every operation.

Type is caller-asserted -- Odyssey doesn't persist or enforce type identity. Passing a
different `Class<T>` for the same name produces deserialization errors at publish or
subscribe time, not declaration-time errors.

Handles are not cached by Odyssey. Each `stream(...)` call returns a fresh handle;
callers that want to reuse one (typical for long-lived streams in a `@Component`) should
hold onto it themselves.

## TTL Policies

Odyssey is deliberately unopinionated about stream lifetimes. There is one default
`TtlPolicy` configured via `odyssey.default-ttl.*` and used when `stream(name, type)` is
called without an explicit policy. If you want "ephemeral / channel / broadcast"
tiering, define your own `TtlPolicy` constants in your app and pass the one you want to
`stream(...)`:

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
var announcements = odyssey.stream("announcements", Announcement.class, BROADCAST);

// Per-request ephemeral
var progress = odyssey.stream(UUID.randomUUID().toString(), TaskProgress.class, EPHEMERAL);
```

Each `TtlPolicy` has three fields: `inactivityTtl` (journal auto-expires if no appends for
this long), `entryTtl` (per-entry TTL applied on every `publish`), and `retentionTtl`
(how long the journal stays readable after `complete()` is called). They don't have to
match -- a broadcast stream might want short `entryTtl` but long `retentionTtl` so
late-joining subscribers can still see recent history after `complete()`.

Remember that TTL is **fixed at the first `stream(...)` call for a given name in the
backend**. Later calls with a different TTL silently adopt the existing policy.

### The idiomatic wrapper: a `Streams` factory

Most apps shouldn't sprinkle `odyssey.stream(...)` calls and raw TTL constants across
controllers. Centralize the name conventions and TTL tiers in one typed factory:

```java
@Component
public class Streams {
    private final Odyssey odyssey;

    public Streams(Odyssey odyssey) { this.odyssey = odyssey; }

    public OdysseyStream<Announcement> announcements() {
        return odyssey.stream("announcements", Announcement.class, TtlPolicies.BROADCAST);
    }
    public OdysseyStream<Notification> userChannel(String userId) {
        return odyssey.stream("user:" + userId, Notification.class, TtlPolicies.CHANNEL);
    }
    public OdysseyStream<TaskProgress> taskProgress(String taskId) {
        return odyssey.stream(taskId, TaskProgress.class, TtlPolicies.EPHEMERAL);
    }
}
```

Controllers inject `Streams` and never touch names, types, or TTL directly:

```java
var id = streams.announcements().publish("message", new Announcement("hi"));
SseEmitter e = streams.userChannel(userId).subscribe();
```

## Subscriber customizers

Per-call configuration for subscribers lives on `SubscriberCustomizer<T>` lambdas passed
as the second argument to `subscribe` / `resume` / `replay`:

```java
SseEmitter e = orders.subscribe(cfg -> {
    cfg.timeout(Duration.ofMinutes(30));
    cfg.keepAliveInterval(Duration.ofSeconds(15));
    cfg.onCompleted(() -> metrics.count("stream.completed"));
    cfg.onSubscribe(emitter ->
        emitter.send(SseEmitter.event().name("hello").data(hostname)));
});
```

`onSubscribe` runs once per subscription, right after the SSE connection opens and before
any journal events flow -- useful for emitting a synthetic first event like an instance
identifier for load-balanced deployments (see the [Example Application](#example-application)
multi-host demo below).

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
orders.subscribe(cfg -> {
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
with a static HTML page demonstrating three TTL tiers (ephemeral tasks, per-user
notifications, and broadcast announcements) all built on `odyssey.stream(name, type, ttl)`.
It ships in two runnable shapes: a single-instance dev mode for local iteration, and a
multi-instance cluster mode that shows Odyssey's real payoff — any instance can publish,
any instance can subscribe, no sticky sessions required.

### Dev mode (single instance)

Start Redis in the background and run the app from your IDE or Maven:

```bash
# Start Redis in the background
cd odyssey-example
docker compose up -d

# In another terminal: run the example
./mvnw -pl odyssey-example spring-boot:run
```

Open http://localhost:8080 to interact with broadcast, channel, and ephemeral streams.

### Cluster mode (multiple instances behind Traefik)

This mode spins up Redis plus Traefik (reverse proxy) plus any number of example
instances. Traefik auto-discovers instances as they come up via Docker labels, so you
can scale up and down at will.

```bash
# Build the app image (uses Spring Boot buildpacks — no Dockerfile)
./mvnw -pl odyssey-example spring-boot:build-image

# Bring up Redis, Traefik, and three example instances
cd odyssey-example
docker compose --profile cluster up --scale example=3
```

- Open http://localhost/ — Traefik round-robins requests across the instances.
- Open http://localhost:8080/ — Traefik dashboard showing the routing table and health.

Each section of the demo page shows:

- **served by** — which instance is handling that SSE stream (delivered as a `whoami`
  event right after the connection opens; differs per stream because each GET may land
  on a different instance).
- **started by** (ephemeral tasks only) — which instance received the `POST /api/task`
  and is running the progress-publisher loop.
- **published via X** (broadcast / notify logs) — which instance handled each `POST`.

You can watch `started by` and `served by` differ on a single task: one instance kicked
off the publisher loop, a different instance is tailing the SSE stream. The journal lives
in Redis, so neither instance needs to know about the other. That's the multi-host story.

Scale up or down at runtime:

```bash
docker compose --profile cluster up --scale example=5   # now five
docker compose --profile cluster up --scale example=1   # back to one
```

Traefik picks up the change from Docker's event stream.

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
