# Spring Boot autoconfiguration and starter

## What to build

Wire everything together with Spring Boot autoconfiguration in `odyssey-autoconfigure` and
the dependency-only `odyssey-spring-boot-starter` module.

**`OdysseyProperties` (`@ConfigurationProperties(prefix = "odyssey")`):**
```yaml
odyssey:
  keep-alive-interval: 30s
  sse-timeout: 0

  redis:
    stream-prefix: "odyssey:"
    max-len: 100000
    max-last-n: 500

    ttl:
      ephemeral: 5m
      channel: 1h
      broadcast: 24h
```

All values should have sensible defaults matching the above.

**`OdysseyAutoConfiguration`:**
- Conditional on `RedisConnectionFactory` being available
- Creates beans:
  - `StatefulRedisPubSubConnection` — dedicated Pub/Sub connection (Connection 1)
  - `StatefulRedisConnection` — shared on-demand connection (Connection 2)
  - `PubSubNotificationListener` — started as `SmartLifecycle`
  - `RedisOdysseyStreamRegistry` — the primary bean consumers inject
- Both connections should be obtained from the `RedisConnectionFactory` (Lettuce)

**`odyssey-spring-boot-starter`:**
- POM only — depends on `odyssey-autoconfigure`
- Transitively pulls in `odyssey-core`, `odyssey-redis`
- This is what consumers add to their project

**Auto-configuration registration:**
- Register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  (Spring Boot 3.x+ / 4.x style)

## Acceptance criteria

- [ ] `OdysseyProperties` binds all config values with correct defaults
- [ ] `OdysseyAutoConfiguration` creates all required beans
- [ ] Auto-config is conditional on `RedisConnectionFactory`
- [ ] Pub/Sub listener starts and stops with the application lifecycle
- [ ] Two distinct Lettuce connections are created (Pub/Sub + shared)
- [ ] `odyssey-spring-boot-starter` POM pulls in all transitive dependencies
- [ ] Auto-configuration is registered in the imports file
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Use `@AutoConfiguration` annotation (Spring Boot 4.x).
- Use `@ConditionalOnClass(RedisConnectionFactory.class)` and
  `@ConditionalOnBean(RedisConnectionFactory.class)`.
- The Pub/Sub connection must be separate from the shared connection — `PSUBSCRIBE` blocks
  the connection.
- Virtual threads must be enabled (`spring.threads.virtual.enabled=true`). Consider adding
  a startup check or documentation note.
