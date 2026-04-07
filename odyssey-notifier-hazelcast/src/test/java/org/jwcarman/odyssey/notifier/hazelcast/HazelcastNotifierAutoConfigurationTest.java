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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyStreamNotifier;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

class HazelcastNotifierAutoConfigurationTest {

  @Test
  void createsHazelcastStreamNotifierBean() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(HazelcastNotifierAutoConfiguration.class))
        .withUserConfiguration(MockHazelcastConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(HazelcastOdysseyStreamNotifier.class);
              assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
            });
  }

  @Test
  void hazelcastNotifierSuppressesInMemoryFallback() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                HazelcastNotifierAutoConfiguration.class, OdysseyAutoConfiguration.class))
        .withUserConfiguration(MockHazelcastConfiguration.class)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
              assertThat(context.getBean(OdysseyStreamNotifier.class))
                  .isInstanceOf(HazelcastOdysseyStreamNotifier.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyStreamNotifier.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class MockHazelcastConfiguration {

    @Bean
    HazelcastInstance hazelcastInstance() {
      HazelcastInstance hazelcast = mock(HazelcastInstance.class);
      ITopic<String> topic = mock();
      doReturn(topic).when(hazelcast).getTopic(anyString());
      return hazelcast;
    }
  }
}
