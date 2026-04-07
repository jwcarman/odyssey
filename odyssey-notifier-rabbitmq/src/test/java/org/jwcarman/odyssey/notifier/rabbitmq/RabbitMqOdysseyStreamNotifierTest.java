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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitMqOdysseyStreamNotifierTest {

  @Mock private RabbitTemplate rabbitTemplate;
  @Mock private ConnectionFactory connectionFactory;

  private RabbitMqOdysseyStreamNotifier notifier;

  @BeforeEach
  void setUp() {
    notifier =
        new RabbitMqOdysseyStreamNotifier(rabbitTemplate, connectionFactory, "odyssey.notify");
  }

  @Test
  void stopWhenNotStartedDoesNotThrow() {
    notifier.stop();
    assertThat(notifier.isRunning()).isFalse();
  }

  @Test
  void notifyPublishesMessageToExchange() {
    notifier.notify("odyssey:channel:test", "42-0");

    org.mockito.Mockito.verify(rabbitTemplate)
        .send(
            org.mockito.ArgumentMatchers.eq("odyssey.notify"),
            org.mockito.ArgumentMatchers.eq(""),
            org.mockito.ArgumentMatchers.any(Message.class));
  }

  @Test
  void subscribeRegistersHandler() {
    List<String> received = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> received.add(streamKey + ":" + eventId));
    assertThat(received).isEmpty();
  }
}
