# Implement odyssey-notifier-sns

## What to build

Create the `odyssey-notifier-sns` module — an `OdysseyStreamNotifier` implementation
backed by Amazon SNS.

**Module setup:**
- `artifactId: odyssey-notifier-sns`
- Depends on `odyssey-core`
- Dependencies: `software.amazon.awssdk:sns`, `software.amazon.awssdk:sqs`

**Architecture:**
SNS is a publish-only service — it pushes to subscribers (SQS, HTTP, Lambda). For
OdySSEy, each node needs to receive notifications. The pattern:

1. One SNS topic: `odyssey-notifications`
2. Each node creates a temporary SQS queue on startup, subscribes it to the SNS topic
3. Node polls the SQS queue for messages on a virtual thread
4. On shutdown, delete the SQS queue (auto-cleanup via SQS queue policy TTL as backup)

**`SnsOdysseyStreamNotifier` implements `OdysseyStreamNotifier`:**
- `notify(streamKey, eventId)`: `SNS.publish()` to the topic. Message body contains
  `streamKey` and `eventId` (JSON or delimited string). Use SNS message attributes for
  the stream key to enable future filtering.
- `subscribe(pattern, handler)`:
  1. Create a temporary SQS queue with a unique name (e.g., `odyssey-<node-id>-<uuid>`)
  2. Subscribe the queue to the SNS topic
  3. Start a virtual thread that long-polls the SQS queue (`receiveMessage` with
     `WaitTimeSeconds: 20`)
  4. On message: parse stream key and event ID, check pattern match, call handler
  5. Delete processed messages from the queue

**`SnsNotifierAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass(SnsClient.class)`
- Creates an `SnsOdysseyStreamNotifier` bean
- Implements `SmartLifecycle` — on stop: unsubscribe and delete the temporary SQS queue
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Configuration:**
```yaml
odyssey:
  notifier:
    sns:
      topic-arn: arn:aws:sns:us-east-1:123456789:odyssey-notifications
      auto-create-topic: false
```

## Acceptance criteria

- [ ] `odyssey-notifier-sns` module exists with correct POM
- [ ] `SnsOdysseyStreamNotifier` implements `OdysseyStreamNotifier`
- [ ] `notify` publishes to the SNS topic with stream key and event ID
- [ ] `subscribe` creates a temporary SQS queue, subscribes to SNS, polls for messages
- [ ] Notifications are dispatched to the handler with correct stream key and event ID
- [ ] Temporary SQS queue is cleaned up on shutdown
- [ ] Auto-configuration registers the bean when SNS client is on the classpath
- [ ] Integration tests with Testcontainers LocalStack (SNS + SQS)
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- SNS + SQS is the standard AWS fan-out pattern. Each node gets its own SQS queue so
  all nodes receive all notifications (same as Redis Pub/Sub fan-out).
- SQS long polling (20 seconds) is efficient — no busy-waiting.
- Set a short `MessageRetentionPeriod` on the temporary queue (e.g., 5 minutes) as a
  safety net for cleanup if the node crashes without deleting the queue.
- Use AWS SDK v2 async clients if needed for performance, but synchronous clients with
  virtual threads should work fine.
- Testcontainers LocalStack supports both SNS and SQS.
