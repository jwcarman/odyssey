package org.jwcarman.odyssey.notifier.postgresql;

import javax.sql.DataSource;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(before = OdysseyAutoConfiguration.class)
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(name = "odyssey.notifier.type", havingValue = "postgresql")
@EnableConfigurationProperties(PostgresNotifierProperties.class)
public class PostgresNotifierAutoConfiguration {

  @Bean
  public PostgresOdysseyStreamNotifier postgresOdysseyStreamNotifier(
      DataSource dataSource, PostgresNotifierProperties properties) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    return new PostgresOdysseyStreamNotifier(
        jdbcTemplate, dataSource, properties.getChannel(), properties.getPollTimeoutMillis());
  }
}
