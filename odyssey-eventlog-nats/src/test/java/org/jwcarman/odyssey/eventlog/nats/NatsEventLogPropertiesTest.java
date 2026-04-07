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
package org.jwcarman.odyssey.eventlog.nats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class NatsEventLogPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(NatsEventLogProperties.class)
  @PropertySource("classpath:odyssey-eventlog-nats-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          NatsEventLogProperties properties = context.getBean(NatsEventLogProperties.class);
          assertEquals("ephemeral:", properties.ephemeralPrefix());
          assertEquals("channel:", properties.channelPrefix());
          assertEquals("broadcast:", properties.broadcastPrefix());
          assertEquals("nats://localhost:4222", properties.url());
          assertEquals("ODYSSEY", properties.streamName());
          assertEquals(Duration.ofHours(1), properties.maxAge());
          assertEquals(100000L, properties.maxMessages());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.eventlog.nats.ephemeral-prefix=eph:",
            "odyssey.eventlog.nats.channel-prefix=ch:",
            "odyssey.eventlog.nats.broadcast-prefix=bc:",
            "odyssey.eventlog.nats.url=nats://remote:4222",
            "odyssey.eventlog.nats.stream-name=CUSTOM",
            "odyssey.eventlog.nats.max-age=2h",
            "odyssey.eventlog.nats.max-messages=50000")
        .run(
            context -> {
              NatsEventLogProperties properties = context.getBean(NatsEventLogProperties.class);
              assertEquals("eph:", properties.ephemeralPrefix());
              assertEquals("ch:", properties.channelPrefix());
              assertEquals("bc:", properties.broadcastPrefix());
              assertEquals("nats://remote:4222", properties.url());
              assertEquals("CUSTOM", properties.streamName());
              assertEquals(Duration.ofHours(2), properties.maxAge());
              assertEquals(50000L, properties.maxMessages());
            });
  }
}
