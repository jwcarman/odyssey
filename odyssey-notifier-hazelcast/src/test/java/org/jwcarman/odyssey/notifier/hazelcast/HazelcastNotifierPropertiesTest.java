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
package org.jwcarman.odyssey.notifier.hazelcast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class HazelcastNotifierPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(HazelcastNotifierProperties.class)
  @PropertySource("classpath:odyssey-notifier-hazelcast-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          HazelcastNotifierProperties properties =
              context.getBean(HazelcastNotifierProperties.class);
          assertEquals("odyssey-notifications", properties.topicName());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues("odyssey.notifier.hazelcast.topic-name=custom-topic")
        .run(
            context -> {
              HazelcastNotifierProperties properties =
                  context.getBean(HazelcastNotifierProperties.class);
              assertEquals("custom-topic", properties.topicName());
            });
  }
}
