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
package org.jwcarman.odyssey.eventlog.hazelcast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class HazelcastEventLogPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(HazelcastEventLogProperties.class)
  @PropertySource("classpath:odyssey-eventlog-hazelcast-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          HazelcastEventLogProperties properties =
              context.getBean(HazelcastEventLogProperties.class);
          assertEquals("odyssey:ephemeral:", properties.ephemeralPrefix());
          assertEquals("odyssey:channel:", properties.channelPrefix());
          assertEquals("odyssey:broadcast:", properties.broadcastPrefix());
          assertEquals(100000, properties.ringbufferCapacity());
          assertEquals(Duration.ofHours(1), properties.ringbufferTtl());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.eventlog.hazelcast.ephemeral-prefix=eph:",
            "odyssey.eventlog.hazelcast.channel-prefix=ch:",
            "odyssey.eventlog.hazelcast.broadcast-prefix=bc:",
            "odyssey.eventlog.hazelcast.ringbuffer-capacity=5000",
            "odyssey.eventlog.hazelcast.ringbuffer-ttl=2h")
        .run(
            context -> {
              HazelcastEventLogProperties properties =
                  context.getBean(HazelcastEventLogProperties.class);
              assertEquals("eph:", properties.ephemeralPrefix());
              assertEquals("ch:", properties.channelPrefix());
              assertEquals("bc:", properties.broadcastPrefix());
              assertEquals(5000, properties.ringbufferCapacity());
              assertEquals(Duration.ofHours(2), properties.ringbufferTtl());
            });
  }
}
