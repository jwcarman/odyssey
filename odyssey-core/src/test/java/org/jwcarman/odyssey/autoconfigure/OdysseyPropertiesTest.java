/*
 * Copyright © 2026 James Carman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwcarman.odyssey.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class OdysseyPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(OdysseyProperties.class)
  @PropertySource("classpath:odyssey-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          OdysseyProperties properties = context.getBean(OdysseyProperties.class);
          assertEquals(Duration.ofSeconds(30), properties.keepAliveInterval());
          assertEquals(Duration.ZERO, properties.sseTimeout());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues("odyssey.keep-alive-interval=10s", "odyssey.sse-timeout=5s")
        .run(
            context -> {
              OdysseyProperties properties = context.getBean(OdysseyProperties.class);
              assertEquals(Duration.ofSeconds(10), properties.keepAliveInterval());
              assertEquals(Duration.ofSeconds(5), properties.sseTimeout());
            });
  }
}
