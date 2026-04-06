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
package org.jwcarman.odyssey.notifier.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyStreamNotifier;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class PostgresNotifierAutoConfigurationTest {

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  @Test
  void createsPostgresNotifierBean() {
    createContextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(PostgresOdysseyStreamNotifier.class);
              assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
            });
  }

  @Test
  void postgresNotifierSuppressesInMemoryFallback() {
    createContextRunner()
        .withConfiguration(AutoConfigurations.of(OdysseyAutoConfiguration.class))
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
              assertThat(context.getBean(OdysseyStreamNotifier.class))
                  .isInstanceOf(PostgresOdysseyStreamNotifier.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyStreamNotifier.class);
            });
  }

  private ApplicationContextRunner createContextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PostgresNotifierAutoConfiguration.class))
        .withBean(
            DataSource.class,
            () -> {
              DriverManagerDataSource ds = new DriverManagerDataSource();
              ds.setUrl(postgres.getJdbcUrl());
              ds.setUsername(postgres.getUsername());
              ds.setPassword(postgres.getPassword());
              return ds;
            });
  }
}
