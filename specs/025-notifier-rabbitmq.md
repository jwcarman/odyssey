# Implement odyssey-notifier-rabbitmq

## What to build

Create the `odyssey-notifier-rabbitmq` module — an `OdysseyStreamNotifier` implementation
backed by RabbitMQ using a fanout exchange.

**Module setup:**
- `artifactId: odyssey-notifier-rabbitmq`
- Depends on `odyssey-core`
- Dependencies: `spring-boot-starter-amqp`

**RabbitMQ topology:**
- Fanout exchange: `odyssey.notifications` — every bound queue receives every message
- Each node creates a temporary, auto-delete, exclusive queue on startup and binds it
  to the exchange
- When the node disconnects, RabbitMQ auto-deletes the queue — zero cleanup

**`RabbitMqOdysseyStreamNotifier` implements `OdysseyStreamNotifier`:**
- `notify(streamKey, eventId)`: publish a message to the `odyssey.notifications` fanout
  exchange. Message body: `streamKey` and `eventId` (JSON or delimited string). Routing
  key is ignored by fanout exchanges.
- `subscribe(pattern, handler)`:
  1. Declare the fanout exchange (idempotent)
  2. Declare a temporary, auto-delete, exclusive queue
  3. Bind the queue to the exchange
  4. Register a `MessageListener` that parses the message, checks the stream key against
     the pattern, and calls `handler.onNotification(streamKey, eventId)`

**`RabbitMqNotifierAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass(ConnectionFactory.class)` (org.springframework.amqp)
- `@ConditionalOnProperty(name = "odyssey.notifier.type", havingValue = "rabbitmq")`
  — disambiguate since `ConnectionFactory` could be on the classpath for other reasons
- Creates a `RabbitMqOdysseyStreamNotifier` bean using Spring AMQP's `RabbitTemplate`
  and `SimpleMessageListenerContainer`
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Configuration:**
```yaml
odyssey:
  notifier:
    rabbitmq:
      exchange-name: odyssey.notifications
```

Spring AMQP uses standard Spring Boot RabbitMQ connection properties:
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
```

## Acceptance criteria

- [ ] `odyssey-notifier-rabbitmq` module exists with correct POM
- [ ] `RabbitMqOdysseyStreamNotifier` implements `OdysseyStreamNotifier`
- [ ] `notify` publishes to the fanout exchange with stream key and event ID
- [ ] `subscribe` creates a temporary queue, binds to the exchange, and listens
- [ ] Notifications are dispatched to the handler with correct stream key and event ID
- [ ] Temporary queue is auto-deleted when the node disconnects
- [ ] Auto-configuration registers the bean when Spring AMQP is on the classpath
- [ ] Integration tests with Testcontainers RabbitMQ
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Use Spring AMQP's `RabbitTemplate` for publishing and `SimpleMessageListenerContainer`
  for consuming. Spring AMQP handles connection management, reconnection, and channel
  pooling.
- Fanout exchanges ignore routing keys — every bound queue gets every message. This
  gives us the same fan-out semantics as Redis Pub/Sub.
- The exclusive queue flag ensures only this connection can consume from it, and
  auto-delete ensures it's removed when the connection closes.
- Pattern matching happens client-side, same as the PostgreSQL notifier.
- RabbitMQ is one of the most widely deployed message brokers — this module will likely
  see significant adoption.
- Testcontainers: `new RabbitMQContainer("rabbitmq:3-management")`.
