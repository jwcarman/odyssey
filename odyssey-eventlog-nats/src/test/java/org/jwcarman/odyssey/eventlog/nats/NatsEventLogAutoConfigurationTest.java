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
package org.jwcarman.odyssey.eventlog.nats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamInfo;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

class NatsEventLogAutoConfigurationTest {

  private static Connection createMockConnection() throws IOException, JetStreamApiException {
    Connection connection = mock(Connection.class);
    JetStream jetStream = mock(JetStream.class);
    JetStreamManagement jsm = mock(JetStreamManagement.class);
    StreamInfo streamInfo = mock(StreamInfo.class);

    when(connection.jetStream()).thenReturn(jetStream);
    when(connection.jetStreamManagement()).thenReturn(jsm);
    when(jsm.addStream(any())).thenReturn(streamInfo);

    return connection;
  }

  @Test
  void createsNatsEventLogBean() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(NatsEventLogAutoConfiguration.class))
        .withUserConfiguration(MockNatsConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(NatsOdysseyEventLog.class);
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
            });
  }

  @Test
  void natsEventLogSuppressesInMemoryFallback() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                NatsEventLogAutoConfiguration.class, OdysseyAutoConfiguration.class))
        .withUserConfiguration(MockNatsConfiguration.class)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
              assertThat(context.getBean(OdysseyEventLog.class))
                  .isInstanceOf(NatsOdysseyEventLog.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyEventLog.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class MockNatsConfiguration {

    @Bean
    Connection natsConnection() throws IOException, JetStreamApiException {
      return createMockConnection();
    }
  }
}
