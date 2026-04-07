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
package org.jwcarman.odyssey.eventlog.cassandra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class CassandraEventLogPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(CassandraEventLogProperties.class)
  @PropertySource("classpath:odyssey-eventlog-cassandra-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          CassandraEventLogProperties properties =
              context.getBean(CassandraEventLogProperties.class);
          assertEquals("ephemeral:", properties.ephemeralPrefix());
          assertEquals("channel:", properties.channelPrefix());
          assertEquals("broadcast:", properties.broadcastPrefix());
          assertEquals(Duration.ofHours(24), properties.defaultTtl());
          assertFalse(properties.autoCreateSchema());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.eventlog.cassandra.ephemeral-prefix=eph:",
            "odyssey.eventlog.cassandra.channel-prefix=ch:",
            "odyssey.eventlog.cassandra.broadcast-prefix=bc:",
            "odyssey.eventlog.cassandra.default-ttl=48h",
            "odyssey.eventlog.cassandra.auto-create-schema=true")
        .run(
            context -> {
              CassandraEventLogProperties properties =
                  context.getBean(CassandraEventLogProperties.class);
              assertEquals("eph:", properties.ephemeralPrefix());
              assertEquals("ch:", properties.channelPrefix());
              assertEquals("bc:", properties.broadcastPrefix());
              assertEquals(Duration.ofHours(48), properties.defaultTtl());
              assertEquals(true, properties.autoCreateSchema());
            });
  }
}
