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
package org.jwcarman.odyssey.eventlog.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RedisEventLogPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(RedisEventLogProperties.class)
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          RedisEventLogProperties properties = context.getBean(RedisEventLogProperties.class);
          assertEquals("odyssey:ephemeral:", properties.getEphemeralPrefix());
          assertEquals("odyssey:channel:", properties.getChannelPrefix());
          assertEquals("odyssey:broadcast:", properties.getBroadcastPrefix());
          assertEquals(100_000, properties.getMaxLen());
          assertEquals(500, properties.getMaxLastN());
          assertEquals(Duration.ofMinutes(5), properties.getEphemeralTtl());
          assertEquals(Duration.ofHours(1), properties.getChannelTtl());
          assertEquals(Duration.ofHours(24), properties.getBroadcastTtl());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.eventlog.redis.ephemeral-prefix=custom:temp:",
            "odyssey.eventlog.redis.channel-prefix=custom:chan:",
            "odyssey.eventlog.redis.broadcast-prefix=custom:bcast:",
            "odyssey.eventlog.redis.max-len=50000",
            "odyssey.eventlog.redis.max-last-n=100",
            "odyssey.eventlog.redis.ephemeral-ttl=2m",
            "odyssey.eventlog.redis.channel-ttl=30m",
            "odyssey.eventlog.redis.broadcast-ttl=12h")
        .run(
            context -> {
              RedisEventLogProperties properties = context.getBean(RedisEventLogProperties.class);
              assertEquals("custom:temp:", properties.getEphemeralPrefix());
              assertEquals("custom:chan:", properties.getChannelPrefix());
              assertEquals("custom:bcast:", properties.getBroadcastPrefix());
              assertEquals(50_000, properties.getMaxLen());
              assertEquals(100, properties.getMaxLastN());
              assertEquals(Duration.ofMinutes(2), properties.getEphemeralTtl());
              assertEquals(Duration.ofMinutes(30), properties.getChannelTtl());
              assertEquals(Duration.ofHours(12), properties.getBroadcastTtl());
            });
  }
}
