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
package org.jwcarman.odyssey.eventlog.rabbitmq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class RabbitMqEventLogPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(RabbitMqEventLogProperties.class)
  @PropertySource("classpath:odyssey-eventlog-rabbitmq-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          RabbitMqEventLogProperties properties = context.getBean(RabbitMqEventLogProperties.class);
          assertEquals("ephemeral:", properties.ephemeralPrefix());
          assertEquals("channel:", properties.channelPrefix());
          assertEquals("broadcast:", properties.broadcastPrefix());
          assertEquals("localhost", properties.host());
          assertEquals(5552, properties.port());
          assertEquals(Duration.ofHours(1), properties.maxAge());
          assertEquals(524288000L, properties.maxLengthBytes());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.eventlog.rabbitmq.ephemeral-prefix=eph:",
            "odyssey.eventlog.rabbitmq.channel-prefix=ch:",
            "odyssey.eventlog.rabbitmq.broadcast-prefix=bc:",
            "odyssey.eventlog.rabbitmq.host=remote",
            "odyssey.eventlog.rabbitmq.port=5553",
            "odyssey.eventlog.rabbitmq.max-age=2h",
            "odyssey.eventlog.rabbitmq.max-length-bytes=1048576000")
        .run(
            context -> {
              RabbitMqEventLogProperties properties =
                  context.getBean(RabbitMqEventLogProperties.class);
              assertEquals("eph:", properties.ephemeralPrefix());
              assertEquals("ch:", properties.channelPrefix());
              assertEquals("bc:", properties.broadcastPrefix());
              assertEquals("remote", properties.host());
              assertEquals(5553, properties.port());
              assertEquals(Duration.ofHours(2), properties.maxAge());
              assertEquals(1048576000L, properties.maxLengthBytes());
            });
  }
}
