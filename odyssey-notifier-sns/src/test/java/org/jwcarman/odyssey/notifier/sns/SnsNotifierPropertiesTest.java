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
package org.jwcarman.odyssey.notifier.sns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

class SnsNotifierPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Configuration
  @EnableConfigurationProperties(SnsNotifierProperties.class)
  @PropertySource("classpath:odyssey-notifier-sns-defaults.properties")
  static class PropertiesConfiguration {}

  @Test
  void defaultValues() {
    contextRunner.run(
        context -> {
          SnsNotifierProperties properties = context.getBean(SnsNotifierProperties.class);
          assertNull(properties.topicArn());
          assertFalse(properties.autoCreateTopic());
          assertEquals(Duration.ofMinutes(5), properties.sqsMessageRetention());
        });
  }

  @Test
  void customValues() {
    contextRunner
        .withPropertyValues(
            "odyssey.notifier.sns.topic-arn=arn:aws:sns:us-east-1:123456789:my-topic",
            "odyssey.notifier.sns.auto-create-topic=true",
            "odyssey.notifier.sns.sqs-message-retention=10m")
        .run(
            context -> {
              SnsNotifierProperties properties = context.getBean(SnsNotifierProperties.class);
              assertEquals("arn:aws:sns:us-east-1:123456789:my-topic", properties.topicArn());
              assertEquals(true, properties.autoCreateTopic());
              assertEquals(Duration.ofMinutes(10), properties.sqsMessageRetention());
            });
  }
}
