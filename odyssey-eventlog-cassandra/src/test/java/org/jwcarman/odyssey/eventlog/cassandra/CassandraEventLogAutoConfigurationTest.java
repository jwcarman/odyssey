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
package org.jwcarman.odyssey.eventlog.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.datastax.oss.driver.api.core.CqlSession;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

class CassandraEventLogAutoConfigurationTest {

  @Test
  void createsCassandraEventLogBean() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CassandraEventLogAutoConfiguration.class))
        .withUserConfiguration(MockCassandraConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(CassandraOdysseyEventLog.class);
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
            });
  }

  @Test
  void cassandraEventLogSuppressesInMemoryFallback() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CassandraEventLogAutoConfiguration.class, OdysseyAutoConfiguration.class))
        .withUserConfiguration(MockCassandraConfiguration.class)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
              assertThat(context.getBean(OdysseyEventLog.class))
                  .isInstanceOf(CassandraOdysseyEventLog.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyEventLog.class);
            });
  }

  @Test
  void doesNotCreateSchemaWhenAutoCreateSchemaIsFalse() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CassandraEventLogAutoConfiguration.class))
        .withUserConfiguration(MockCassandraConfiguration.class)
        .withPropertyValues("odyssey.eventlog.cassandra.auto-create-schema=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(CassandraOdysseyEventLog.class);
              CqlSession mockSession = context.getBean(CqlSession.class);
              org.mockito.Mockito.verify(mockSession, org.mockito.Mockito.never())
                  .execute(org.mockito.ArgumentMatchers.anyString());
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class MockCassandraConfiguration {

    @Bean
    CqlSession cqlSession() {
      return mock(CqlSession.class);
    }
  }
}
