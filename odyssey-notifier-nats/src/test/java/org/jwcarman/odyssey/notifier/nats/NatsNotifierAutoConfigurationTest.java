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
package org.jwcarman.odyssey.notifier.nats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.MessageHandler;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyStreamNotifier;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class NatsNotifierAutoConfigurationTest {

  @Test
  void createsNatsStreamNotifierBean() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(NatsNotifierAutoConfiguration.class))
        .withUserConfiguration(MockNatsConfiguration.class)
        .withPropertyValues("odyssey.notifier.type=nats")
        .run(
            context -> {
              assertThat(context).hasSingleBean(NatsOdysseyStreamNotifier.class);
              assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
            });
  }

  @Test
  void natsNotifierSuppressesInMemoryFallback() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                NatsNotifierAutoConfiguration.class, OdysseyAutoConfiguration.class))
        .withUserConfiguration(MockNatsConfiguration.class)
        .withPropertyValues("odyssey.notifier.type=nats")
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
              assertThat(context.getBean(OdysseyStreamNotifier.class))
                  .isInstanceOf(NatsOdysseyStreamNotifier.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyStreamNotifier.class);
            });
  }

  @Test
  void doesNotActivateWithoutNatsProperty() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(NatsNotifierAutoConfiguration.class))
        .withUserConfiguration(MockNatsConfiguration.class)
        .run(context -> assertThat(context).doesNotHaveBean(NatsOdysseyStreamNotifier.class));
  }

  @Configuration(proxyBeanMethods = false)
  static class MockNatsConfiguration {

    @Bean
    Connection natsConnection() {
      Connection connection = mock(Connection.class);
      Dispatcher dispatcher = mock(Dispatcher.class);
      when(connection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);
      return connection;
    }
  }
}
