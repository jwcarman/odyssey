# Implement odyssey-notifier-sqs

## What to build

Create the `odyssey-notifier-sqs` module — an `OdysseyStreamNotifier` implementation
backed by Amazon SQS directly (without SNS).

This is a simpler alternative to the SNS module for environments where SNS is not
available or desired. The trade-off: each node must know about a shared SQS queue, and
all nodes poll the same queue — meaning each notification is received by only ONE node
(competing consumers), not all nodes.

**Important limitation:** SQS is a point-to-point queue, not a fan-out pub/sub system.
A message polled by one node is invisible to other nodes. This means this notifier only
works correctly in **single-node deployments** or deployments where each stream key's
subscribers are guaranteed to be on a single node.

For multi-node fan-out, use `odyssey-notifier-sns` (which uses SQS under the hood with
per-node queues) or another fan-out notifier.

**Module setup:**
- `artifactId: odyssey-notifier-sqs`
- Depends on `odyssey-core`
- Dependencies: `software.amazon.awssdk:sqs`

**`SqsOdysseyStreamNotifier` implements `OdysseyStreamNotifier`:**
- `notify(streamKey, eventId)`: `SQS.sendMessage()` to the configured queue. Message
  body contains `streamKey` and `eventId`. Use SQS message attributes for the stream key.
- `subscribe(pattern, handler)`: start a virtual thread that long-polls the SQS queue
  (`receiveMessage` with `WaitTimeSeconds: 20`). On message: parse stream key and event
  ID, check pattern match, call handler, delete the message.

**`SqsNotifierAutoConfiguration`:**
- `@AutoConfiguration`
- `@ConditionalOnClass(SqsClient.class)`
- `@ConditionalOnProperty(name = "odyssey.notifier.type", havingValue = "sqs")`
  — needed to disambiguate from SNS (which also uses SQS)
- Creates an `SqsOdysseyStreamNotifier` bean
- Implements `SmartLifecycle`
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Configuration:**
```yaml
odyssey:
  notifier:
    sqs:
      queue-url: https://sqs.us-east-1.amazonaws.com/123456789/odyssey-notifications
```

## Acceptance criteria

- [ ] `odyssey-notifier-sqs` module exists with correct POM
- [ ] `SqsOdysseyStreamNotifier` implements `OdysseyStreamNotifier`
- [ ] `notify` sends a message to the SQS queue
- [ ] `subscribe` polls the queue and dispatches to the handler
- [ ] Messages are deleted after processing
- [ ] Auto-configuration registers the bean correctly
- [ ] Limitation (single-node only) is documented in the module's README
- [ ] Integration tests with Testcontainers LocalStack (SQS)
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- SQS long polling is efficient — `WaitTimeSeconds: 20` avoids busy-waiting.
- This module is the simplest AWS notifier option but has the competing-consumer
  limitation. Document this clearly.
- Consider whether this module is worth shipping given the limitation. It may be most
  useful as the "easy AWS option for single-node" that pairs with DynamoDB event log.
- Use `@ConditionalOnProperty` to disambiguate from SNS since both modules would have
  `SqsClient` on the classpath if the user has the AWS SDK.
