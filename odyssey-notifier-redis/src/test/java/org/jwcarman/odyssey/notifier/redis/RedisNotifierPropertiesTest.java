package org.jwcarman.odyssey.notifier.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RedisNotifierPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(RedisNotifierProperties.class)
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          RedisNotifierProperties properties = context.getBean(RedisNotifierProperties.class);
          assertEquals("odyssey:notify:", properties.getChannelPrefix());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues("odyssey.notifier.redis.channel-prefix=custom:notify:")
        .run(
            context -> {
              RedisNotifierProperties properties = context.getBean(RedisNotifierProperties.class);
              assertEquals("custom:notify:", properties.getChannelPrefix());
            });
  }
}
