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
package org.jwcarman.odyssey.notifier.rabbitmq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class RabbitMqNotifierPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(RabbitMqNotifierProperties.class)
  @PropertySource("classpath:odyssey-notifier-rabbitmq-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          RabbitMqNotifierProperties properties = context.getBean(RabbitMqNotifierProperties.class);
          assertEquals("odyssey.notifications", properties.exchangeName());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues("odyssey.notifier.rabbitmq.exchange-name=custom.notifications")
        .run(
            context -> {
              RabbitMqNotifierProperties properties =
                  context.getBean(RabbitMqNotifierProperties.class);
              assertEquals("custom.notifications", properties.exchangeName());
            });
  }
}
