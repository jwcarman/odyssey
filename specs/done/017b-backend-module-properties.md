# Add properties classes to backend modules

## What to build

Each backend module should own its own `@ConfigurationProperties` class rather than
relying on a shared monolithic config.

### `odyssey-eventlog-redis`

Create `RedisEventLogProperties` at `org.jwcarman.odyssey.eventlog.redis`:

```java
@ConfigurationProperties(prefix = "odyssey.eventlog.redis")
public class RedisEventLogProperties {
    private String streamPrefix = "odyssey:";
    private long maxLen = 100_000;
    private int maxLastN = 500;
    private Duration ephemeralTtl = Duration.ofMinutes(5);
    private Duration channelTtl = Duration.ofHours(1);
    private Duration broadcastTtl = Duration.ofHours(24);
}
```

Wire this into `RedisEventLogAutoConfiguration` and use it in `RedisOdysseyEventLog`.

### `odyssey-notifier-redis`

Create `RedisNotifierProperties` at `org.jwcarman.odyssey.notifier.redis`:

```java
@ConfigurationProperties(prefix = "odyssey.notifier.redis")
public class RedisNotifierProperties {
    private String channelPrefix = "odyssey:notify:";
}
```

Wire this into `RedisNotifierAutoConfiguration` and use it in
`RedisOdysseyStreamNotifier` for building the `PSUBSCRIBE` pattern and the `PUBLISH`
channel name.

### Cleanup

- Remove any Redis-specific configuration from `OdysseyProperties` in `odyssey-core`.
  Core should only have backend-agnostic properties (`keepAliveInterval`, `sseTimeout`,
  `maxLastN`).
- Update auto-configuration classes to `@EnableConfigurationProperties` for their
  respective properties classes.

## Acceptance criteria

- [ ] `RedisEventLogProperties` exists with all Redis event log config
- [ ] `RedisNotifierProperties` exists with channel prefix config
- [ ] Both are wired into their respective auto-configuration classes
- [ ] `RedisOdysseyEventLog` uses `RedisEventLogProperties` for stream prefix, maxLen, TTLs
- [ ] `RedisOdysseyStreamNotifier` uses `RedisNotifierProperties` for channel prefix
- [ ] Core `OdysseyProperties` has no Redis-specific configuration
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Each backend module's properties are self-contained — if the module isn't on the
  classpath, its properties don't exist. Clean separation.
- Future backend modules (PostgreSQL, Cassandra, etc.) will follow the same pattern
  with their own `@ConfigurationProperties`.
