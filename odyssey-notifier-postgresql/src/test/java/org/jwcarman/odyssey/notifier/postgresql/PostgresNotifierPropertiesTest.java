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
package org.jwcarman.odyssey.notifier.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class PostgresNotifierPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(PostgresNotifierProperties.class)
  @PropertySource("classpath:odyssey-notifier-postgresql-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          PostgresNotifierProperties properties = context.getBean(PostgresNotifierProperties.class);
          assertEquals("odyssey_notify", properties.channel());
          assertEquals(Duration.ofMillis(500), properties.pollTimeout());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.notifier.postgresql.channel=custom_notify",
            "odyssey.notifier.postgresql.poll-timeout=1s")
        .run(
            context -> {
              PostgresNotifierProperties properties =
                  context.getBean(PostgresNotifierProperties.class);
              assertEquals("custom_notify", properties.channel());
              assertEquals(Duration.ofSeconds(1), properties.pollTimeout());
            });
  }
}
