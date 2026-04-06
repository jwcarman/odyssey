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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class DynamoDbEventLogAutoConfigurationIT {

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
          .withServices(LocalStackContainer.Service.DYNAMODB);

  @Test
  void createsDynamoDbEventLogBean() {
    createContextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(DynamoDbOdysseyEventLog.class);
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
            });
  }

  @Test
  void dynamoDbEventLogSuppressesInMemoryFallback() {
    createContextRunner()
        .withConfiguration(AutoConfigurations.of(OdysseyAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
              assertThat(context.getBean(OdysseyEventLog.class))
                  .isInstanceOf(DynamoDbOdysseyEventLog.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyEventLog.class);
            });
  }

  private ApplicationContextRunner createContextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DynamoDbEventLogAutoConfiguration.class))
        .withPropertyValues("odyssey.eventlog.dynamodb.auto-create-table=true")
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(
            DynamoDbClient.class,
            () ->
                DynamoDbClient.builder()
                    .endpointOverride(
                        URI.create(
                            localstack
                                .getEndpointOverride(LocalStackContainer.Service.DYNAMODB)
                                .toString()))
                    .credentialsProvider(
                        StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                localstack.getAccessKey(), localstack.getSecretKey())))
                    .region(Region.of(localstack.getRegion()))
                    .build());
  }
}
