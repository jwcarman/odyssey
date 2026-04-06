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
package org.jwcarman.odyssey.eventlog.dynamodb;

import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@AutoConfiguration(before = OdysseyAutoConfiguration.class)
@ConditionalOnClass(DynamoDbClient.class)
@EnableConfigurationProperties(DynamoDbEventLogProperties.class)
@PropertySource("classpath:odyssey-eventlog-dynamodb-defaults.properties")
public class DynamoDbEventLogAutoConfiguration {

  @Bean
  public DynamoDbOdysseyEventLog dynamoDbOdysseyEventLog(
      DynamoDbClient dynamoDbClient, DynamoDbEventLogProperties properties) {
    DynamoDbOdysseyEventLog eventLog =
        new DynamoDbOdysseyEventLog(
            dynamoDbClient,
            properties.tableName(),
            properties.ttlSeconds(),
            properties.ephemeralPrefix(),
            properties.channelPrefix(),
            properties.broadcastPrefix());

    if (properties.autoCreateTable()) {
      eventLog.createTable();
    }

    return eventLog;
  }
}
