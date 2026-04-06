package org.jwcarman.odyssey.eventlog.postgresql;

import javax.sql.DataSource;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@AutoConfiguration(before = OdysseyAutoConfiguration.class)
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(name = "odyssey.eventlog.type", havingValue = "postgresql")
@EnableConfigurationProperties(PostgresEventLogProperties.class)
public class PostgresEventLogAutoConfiguration {

  @Bean
  public PostgresOdysseyEventLog postgresOdysseyEventLog(
      DataSource dataSource, PostgresEventLogProperties properties) {
    if (properties.isAutoCreateSchema()) {
      ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
      populator.addScript(new ClassPathResource("db/odyssey/postgresql/V1__create_events.sql"));
      populator.execute(dataSource);
    }

    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    return new PostgresOdysseyEventLog(
        jdbcTemplate,
        properties.getMaxLen(),
        properties.getEphemeralPrefix(),
        properties.getChannelPrefix(),
        properties.getBroadcastPrefix());
  }
}
