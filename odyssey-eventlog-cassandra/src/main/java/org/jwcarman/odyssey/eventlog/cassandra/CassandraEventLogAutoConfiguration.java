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

import com.datastax.oss.driver.api.core.CqlSession;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;

@AutoConfiguration(before = OdysseyAutoConfiguration.class)
@ConditionalOnClass(CqlSession.class)
@EnableConfigurationProperties(CassandraEventLogProperties.class)
@PropertySource("classpath:odyssey-eventlog-cassandra-defaults.properties")
public class CassandraEventLogAutoConfiguration {

  @Bean
  public CassandraOdysseyEventLog cassandraOdysseyEventLog(
      CqlSession cqlSession, CassandraEventLogProperties properties) {
    if (properties.autoCreateSchema()) {
      ClassPathResource schemaResource =
          new ClassPathResource("db/odyssey/cassandra/V1__create_events.cql");
      try {
        String cql = new String(schemaResource.getInputStream().readAllBytes());
        cqlSession.execute(cql);
      } catch (Exception e) {
        throw new RuntimeException("Failed to execute Cassandra schema", e);
      }
    }

    return new CassandraOdysseyEventLog(
        cqlSession,
        properties.defaultTtlSeconds(),
        properties.ephemeralPrefix(),
        properties.channelPrefix(),
        properties.broadcastPrefix());
  }
}
