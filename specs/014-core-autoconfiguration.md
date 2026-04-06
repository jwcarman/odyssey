# Core auto-configuration with in-memory fallback

## What to build

Create the auto-configuration in `odyssey-core` under
`org.jwcarman.odyssey.autoconfigure` that wires the engine beans and provides in-memory
fallbacks.

**`OdysseyProperties` (`@ConfigurationProperties(prefix = "odyssey")`):**
```yaml
odyssey:
  keep-alive-interval: 30s
  sse-timeout: 0
  stream-prefix: "odyssey:"
  max-len: 100000
  max-last-n: 500
  ttl:
    ephemeral: 5m
    channel: 1h
    broadcast: 24h
```

**`OdysseyAutoConfiguration`:**

Beans to create:
- `DefaultOdysseyStreamRegistry` — always created, wired to whatever `OdysseyEventLog` and
  `OdysseyStreamNotifier` beans are available
- `InMemoryOdysseyEventLog` — `@ConditionalOnMissingBean(OdysseyEventLog.class)`. On
  activation, log a WARN:
  ```
  No OdysseyEventLog bean found; falling back to in-memory implementation.
  Suitable for single-node environments and testing only.
  For clustered deployments, add a backend module (e.g. odyssey-eventlog-redis).
  ```
- `InMemoryOdysseyStreamNotifier` — `@ConditionalOnMissingBean(OdysseyStreamNotifier.class)`.
  Same WARN pattern.

**Registration:**
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## Acceptance criteria

- [ ] `OdysseyProperties` binds all config values with correct defaults
- [ ] `OdysseyAutoConfiguration` creates `DefaultOdysseyStreamRegistry`
- [ ] In-memory `OdysseyEventLog` activates when no other bean exists
- [ ] In-memory `OdysseyStreamNotifier` activates when no other bean exists
- [ ] WARN log message is printed for each in-memory fallback
- [ ] When external beans are present, in-memory fallbacks do not activate
- [ ] Auto-configuration is registered in the imports file
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Use `@AutoConfiguration` annotation (Spring Boot 4.x).
- The engine needs to start the notification subscription on startup — consider
  `SmartLifecycle` on the registry or a `@PostConstruct` that calls
  `notifier.subscribe(prefix + "notify:*", handler)`.
