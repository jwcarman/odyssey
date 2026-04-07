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
package org.jwcarman.odyssey.eventlog.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class MongoEventLogPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(MongoEventLogProperties.class)
  @PropertySource("classpath:odyssey-eventlog-mongodb-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          MongoEventLogProperties properties = context.getBean(MongoEventLogProperties.class);
          assertEquals("ephemeral:", properties.ephemeralPrefix());
          assertEquals("channel:", properties.channelPrefix());
          assertEquals("broadcast:", properties.broadcastPrefix());
          assertEquals("odyssey_events", properties.collectionName());
          assertEquals(Duration.ofHours(24), properties.ttl());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.eventlog.mongodb.ephemeral-prefix=eph:",
            "odyssey.eventlog.mongodb.channel-prefix=ch:",
            "odyssey.eventlog.mongodb.broadcast-prefix=bc:",
            "odyssey.eventlog.mongodb.collection-name=custom_events",
            "odyssey.eventlog.mongodb.ttl=48h")
        .run(
            context -> {
              MongoEventLogProperties properties = context.getBean(MongoEventLogProperties.class);
              assertEquals("eph:", properties.ephemeralPrefix());
              assertEquals("ch:", properties.channelPrefix());
              assertEquals("bc:", properties.broadcastPrefix());
              assertEquals("custom_events", properties.collectionName());
              assertEquals(Duration.ofHours(48), properties.ttl());
            });
  }
}
