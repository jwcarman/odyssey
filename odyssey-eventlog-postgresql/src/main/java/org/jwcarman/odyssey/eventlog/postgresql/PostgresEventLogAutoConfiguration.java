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
package org.jwcarman.odyssey.eventlog.postgresql;

import javax.sql.DataSource;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@AutoConfiguration(before = OdysseyAutoConfiguration.class)
@ConditionalOnClass(DataSource.class)
@EnableConfigurationProperties(PostgresEventLogProperties.class)
@PropertySource("classpath:odyssey-eventlog-postgresql-defaults.properties")
public class PostgresEventLogAutoConfiguration {

  @Bean
  public PostgresOdysseyEventLog postgresOdysseyEventLog(
      DataSource dataSource, PostgresEventLogProperties properties) {
    if (properties.autoCreateSchema()) {
      ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
      populator.addScript(new ClassPathResource("db/odyssey/postgresql/V1__create_events.sql"));
      populator.execute(dataSource);
    }

    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    return new PostgresOdysseyEventLog(
        jdbcTemplate,
        properties.maxLen(),
        properties.ephemeralPrefix(),
        properties.channelPrefix(),
        properties.broadcastPrefix());
  }
}
