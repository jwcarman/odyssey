package org.jwcarman.odyssey.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OdysseyPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(OdysseyProperties.class)
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          OdysseyProperties properties = context.getBean(OdysseyProperties.class);
          assertEquals(Duration.ofSeconds(30), properties.getKeepAliveInterval());
          assertEquals(Duration.ZERO, properties.getSseTimeout());
          assertEquals("odyssey:", properties.getRedis().getStreamPrefix());
          assertEquals(100_000, properties.getRedis().getMaxLen());
          assertEquals(500, properties.getRedis().getMaxLastN());
          assertEquals(Duration.ofMinutes(5), properties.getRedis().getTtl().getEphemeral());
          assertEquals(Duration.ofHours(1), properties.getRedis().getTtl().getChannel());
          assertEquals(Duration.ofHours(24), properties.getRedis().getTtl().getBroadcast());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.keep-alive-interval=10s",
            "odyssey.sse-timeout=5s",
            "odyssey.redis.stream-prefix=custom:",
            "odyssey.redis.max-len=50000",
            "odyssey.redis.max-last-n=100",
            "odyssey.redis.ttl.ephemeral=2m",
            "odyssey.redis.ttl.channel=30m",
            "odyssey.redis.ttl.broadcast=12h")
        .run(
            context -> {
              OdysseyProperties properties = context.getBean(OdysseyProperties.class);
              assertEquals(Duration.ofSeconds(10), properties.getKeepAliveInterval());
              assertEquals(Duration.ofSeconds(5), properties.getSseTimeout());
              assertEquals("custom:", properties.getRedis().getStreamPrefix());
              assertEquals(50_000, properties.getRedis().getMaxLen());
              assertEquals(100, properties.getRedis().getMaxLastN());
              assertEquals(Duration.ofMinutes(2), properties.getRedis().getTtl().getEphemeral());
              assertEquals(Duration.ofMinutes(30), properties.getRedis().getTtl().getChannel());
              assertEquals(Duration.ofHours(12), properties.getRedis().getTtl().getBroadcast());
            });
  }
}
