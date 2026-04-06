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
package org.jwcarman.odyssey.notifier.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.impl.AMQImpl;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyStreamNotifier;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

class RabbitMqNotifierAutoConfigurationTest {

  @Test
  void createsRabbitMqStreamNotifierBean() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RabbitMqNotifierAutoConfiguration.class))
        .withUserConfiguration(MockRabbitMqConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(RabbitMqOdysseyStreamNotifier.class);
              assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
            });
  }

  @Test
  void rabbitMqNotifierSuppressesInMemoryFallback() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                RabbitMqNotifierAutoConfiguration.class, OdysseyAutoConfiguration.class))
        .withUserConfiguration(MockRabbitMqConfiguration.class)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
              assertThat(context.getBean(OdysseyStreamNotifier.class))
                  .isInstanceOf(RabbitMqOdysseyStreamNotifier.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyStreamNotifier.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class MockRabbitMqConfiguration {

    @Bean
    ConnectionFactory connectionFactory() throws Exception {
      Channel channel = mock(Channel.class);
      when(channel.exchangeDeclare(
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyString(),
              anyBoolean(),
              anyBoolean(),
              org.mockito.ArgumentMatchers.any()))
          .thenReturn(new AMQImpl.Exchange.DeclareOk());
      when(channel.queueDeclare(
              org.mockito.ArgumentMatchers.anyString(),
              anyBoolean(),
              anyBoolean(),
              anyBoolean(),
              org.mockito.ArgumentMatchers.any()))
          .thenReturn(new AMQImpl.Queue.DeclareOk("amq.gen-test", 0, 0));
      when(channel.queueBind(
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.any()))
          .thenReturn(new AMQImpl.Queue.BindOk());
      when(channel.isOpen()).thenReturn(true);

      Connection connection = mock(Connection.class);
      when(connection.createChannel(anyBoolean())).thenReturn(channel);
      when(connection.isOpen()).thenReturn(true);

      ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
      when(connectionFactory.createConnection()).thenReturn(connection);
      return connectionFactory;
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
      return new RabbitTemplate(connectionFactory);
    }
  }
}
