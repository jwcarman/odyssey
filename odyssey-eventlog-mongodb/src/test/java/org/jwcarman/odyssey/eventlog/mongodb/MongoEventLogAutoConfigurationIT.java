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
package org.jwcarman.odyssey.eventlog.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class MongoEventLogAutoConfigurationIT {

  @Container static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7");

  @Test
  void createsMongoEventLogBean() {
    createContextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(MongoOdysseyEventLog.class);
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
            });
  }

  @Test
  void mongoEventLogSuppressesInMemoryFallback() {
    createContextRunner()
        .withConfiguration(AutoConfigurations.of(OdysseyAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
              assertThat(context.getBean(OdysseyEventLog.class))
                  .isInstanceOf(MongoOdysseyEventLog.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyEventLog.class);
            });
  }

  private ApplicationContextRunner createContextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MongoEventLogAutoConfiguration.class))
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(
            MongoTemplate.class,
            () ->
                new MongoTemplate(
                    MongoClients.create(mongoContainer.getConnectionString()), "odyssey_test"));
  }
}
