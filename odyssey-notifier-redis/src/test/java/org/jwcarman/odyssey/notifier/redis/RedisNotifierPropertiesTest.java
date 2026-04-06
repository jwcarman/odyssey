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
package org.jwcarman.odyssey.notifier.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class RedisNotifierPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(RedisNotifierProperties.class)
  @PropertySource("classpath:odyssey-notifier-redis-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          RedisNotifierProperties properties = context.getBean(RedisNotifierProperties.class);
          assertEquals("odyssey:notify:", properties.channelPrefix());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues("odyssey.notifier.redis.channel-prefix=custom:notify:")
        .run(
            context -> {
              RedisNotifierProperties properties = context.getBean(RedisNotifierProperties.class);
              assertEquals("custom:notify:", properties.channelPrefix());
            });
  }
}
