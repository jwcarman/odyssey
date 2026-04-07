# Delete custom LocalStackContainer and use Testcontainers 2.x

## What to build

Ralph copy-pasted the entire `LocalStackContainer` class into the project source
tree at `org/testcontainers/localstack/LocalStackContainer.java`. This is 236 lines
of code we don't own, uses Lombok, and duplicates what Testcontainers 2.x provides.

### Delete the custom class

Delete `org/testcontainers/localstack/LocalStackContainer.java` from the project
root (it's not even in a module — it's a loose file).

### Update all tests to use Testcontainers 2.x LocalStackContainer

The correct class is `org.testcontainers.localstack.LocalStackContainer` from the
`testcontainers-localstack` artifact (Testcontainers 2.x).

**Key differences from the deprecated version:**
- Package: `org.testcontainers.localstack` (not `org.testcontainers.containers.localstack`)
- Not generic — use `LocalStackContainer` not `LocalStackContainer<>`
- Services are passed as strings: `.withServices("sns", "sqs", "dynamodb")`
  (not `Service.SNS` enum — check if the enum still exists in 2.x)
- `getEndpoint()` replaces `getEndpointOverride(Service.X)`

### Add dependency where needed

Add `testcontainers-localstack` as a test dependency in modules that use it:
- `odyssey-notifier-sns`
- `odyssey-eventlog-dynamodb`

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-localstack</artifactId>
    <scope>test</scope>
</dependency>
```

## Acceptance criteria

- [ ] Custom `LocalStackContainer.java` deleted from project root
- [ ] All IT tests use `org.testcontainers.localstack.LocalStackContainer`
- [ ] No references to deprecated `org.testcontainers.containers.localstack`
- [ ] No Lombok dependency introduced
- [ ] `testcontainers-localstack` added as test dependency where needed
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes
