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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

class HazelcastEventLogAutoConfigurationTest {

  @Test
  void createsHazelcastEventLogBean() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(HazelcastEventLogAutoConfiguration.class))
        .withUserConfiguration(MockHazelcastConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(HazelcastOdysseyEventLog.class);
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
            });
  }

  @Test
  void hazelcastEventLogSuppressesInMemoryFallback() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                HazelcastEventLogAutoConfiguration.class, OdysseyAutoConfiguration.class))
        .withUserConfiguration(MockHazelcastConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
              assertThat(context.getBean(OdysseyEventLog.class))
                  .isInstanceOf(HazelcastOdysseyEventLog.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyEventLog.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class MockHazelcastConfiguration {

    @Bean
    HazelcastInstance hazelcastInstance() {
      HazelcastInstance hazelcast = mock(HazelcastInstance.class);
      when(hazelcast.getConfig()).thenReturn(new Config());
      return hazelcast;
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
