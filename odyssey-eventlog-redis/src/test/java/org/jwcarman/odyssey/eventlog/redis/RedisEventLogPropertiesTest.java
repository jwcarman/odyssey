package org.jwcarman.odyssey.eventlog.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RedisEventLogPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(RedisEventLogProperties.class)
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          RedisEventLogProperties properties = context.getBean(RedisEventLogProperties.class);
          assertEquals("odyssey:", properties.getStreamPrefix());
          assertEquals(100_000, properties.getMaxLen());
          assertEquals(500, properties.getMaxLastN());
          assertEquals(Duration.ofMinutes(5), properties.getEphemeralTtl());
          assertEquals(Duration.ofHours(1), properties.getChannelTtl());
          assertEquals(Duration.ofHours(24), properties.getBroadcastTtl());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.eventlog.redis.stream-prefix=custom:",
            "odyssey.eventlog.redis.max-len=50000",
            "odyssey.eventlog.redis.max-last-n=100",
            "odyssey.eventlog.redis.ephemeral-ttl=2m",
            "odyssey.eventlog.redis.channel-ttl=30m",
            "odyssey.eventlog.redis.broadcast-ttl=12h")
        .run(
            context -> {
              RedisEventLogProperties properties = context.getBean(RedisEventLogProperties.class);
              assertEquals("custom:", properties.getStreamPrefix());
              assertEquals(50_000, properties.getMaxLen());
              assertEquals(100, properties.getMaxLastN());
              assertEquals(Duration.ofMinutes(2), properties.getEphemeralTtl());
              assertEquals(Duration.ofMinutes(30), properties.getChannelTtl());
              assertEquals(Duration.ofHours(12), properties.getBroadcastTtl());
            });
  }
}
