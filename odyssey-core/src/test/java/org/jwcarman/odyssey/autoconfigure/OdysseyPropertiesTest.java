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
          assertEquals(500, properties.getMaxLastN());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.keep-alive-interval=10s", "odyssey.sse-timeout=5s", "odyssey.max-last-n=100")
        .run(
            context -> {
              OdysseyProperties properties = context.getBean(OdysseyProperties.class);
              assertEquals(Duration.ofSeconds(10), properties.getKeepAliveInterval());
              assertEquals(Duration.ofSeconds(5), properties.getSseTimeout());
              assertEquals(100, properties.getMaxLastN());
            });
  }
}
