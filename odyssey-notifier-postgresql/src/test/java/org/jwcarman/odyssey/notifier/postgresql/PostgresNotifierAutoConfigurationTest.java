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
        .withPropertyValues("odyssey.notifier.type=postgresql")
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
