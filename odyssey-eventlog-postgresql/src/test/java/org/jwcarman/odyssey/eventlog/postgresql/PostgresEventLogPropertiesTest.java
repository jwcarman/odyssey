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
package org.jwcarman.odyssey.eventlog.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class PostgresEventLogPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(PostgresEventLogProperties.class)
  @PropertySource("classpath:odyssey-eventlog-postgresql-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          PostgresEventLogProperties properties = context.getBean(PostgresEventLogProperties.class);
          assertEquals("ephemeral:", properties.ephemeralPrefix());
          assertEquals("channel:", properties.channelPrefix());
          assertEquals("broadcast:", properties.broadcastPrefix());
          assertEquals(100000L, properties.maxLen());
          assertFalse(properties.autoCreateSchema());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.eventlog.postgresql.ephemeral-prefix=eph:",
            "odyssey.eventlog.postgresql.channel-prefix=ch:",
            "odyssey.eventlog.postgresql.broadcast-prefix=bc:",
            "odyssey.eventlog.postgresql.max-len=50000",
            "odyssey.eventlog.postgresql.auto-create-schema=true")
        .run(
            context -> {
              PostgresEventLogProperties properties =
                  context.getBean(PostgresEventLogProperties.class);
              assertEquals("eph:", properties.ephemeralPrefix());
              assertEquals("ch:", properties.channelPrefix());
              assertEquals("bc:", properties.broadcastPrefix());
              assertEquals(50000L, properties.maxLen());
              assertEquals(true, properties.autoCreateSchema());
            });
  }
}
