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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@Testcontainers
class RabbitMqOdysseyStreamNotifierIT {

  @Container
  static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3-management");

  private CachingConnectionFactory connectionFactory;
  private RabbitMqOdysseyStreamNotifier notifier;

  @BeforeEach
  void setUp() {
    connectionFactory = new CachingConnectionFactory();
    connectionFactory.setHost(rabbitMQContainer.getHost());
    connectionFactory.setPort(rabbitMQContainer.getAmqpPort());
    connectionFactory.setUsername(rabbitMQContainer.getAdminUsername());
    connectionFactory.setPassword(rabbitMQContainer.getAdminPassword());

    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    notifier =
        new RabbitMqOdysseyStreamNotifier(
            rabbitTemplate, connectionFactory, "odyssey.notifications");
  }

  @AfterEach
  void tearDown() {
    if (notifier.isRunning()) {
      notifier.stop();
    }
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void notifyAndSubscribeRoundTrip() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> receivedStreamKeys = new CopyOnWriteArrayList<>();
    List<String> receivedEventIds = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          receivedStreamKeys.add(streamKey);
          receivedEventIds.add(eventId);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:channel:test", "100-0");

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(receivedStreamKeys).containsExactly("odyssey:channel:test");
    assertThat(receivedEventIds).containsExactly("100-0");
  }

  @Test
  void multipleHandlersAllReceiveNotifications() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    List<String> handler1Received = new CopyOnWriteArrayList<>();
    List<String> handler2Received = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          handler1Received.add(eventId);
          latch.countDown();
        });
    notifier.subscribe(
        (streamKey, eventId) -> {
          handler2Received.add(eventId);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:broadcast:news", "5-0");

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(handler1Received).containsExactly("5-0");
    assertThat(handler2Received).containsExactly("5-0");
  }

  @Test
  void multipleNotificationsAreDelivered() throws Exception {
    CountDownLatch latch = new CountDownLatch(3);
    List<String> receivedEventIds = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          receivedEventIds.add(eventId);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:channel:test", "1-0");
    notifier.notify("odyssey:channel:test", "2-0");
    notifier.notify("odyssey:channel:test", "3-0");

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(receivedEventIds).containsExactly("1-0", "2-0", "3-0");
  }

  @Test
  void stopPreventsNewNotifications() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> receivedEventIds = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          receivedEventIds.add(eventId);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:channel:test", "1-0");
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

    notifier.stop();
    notifier.notify("odyssey:channel:test", "2-0");

    Thread.sleep(500);
    assertThat(receivedEventIds).containsExactly("1-0");
  }

  @Test
  void fanoutExchangeDeliversToMultipleConsumers() throws Exception {
    RabbitTemplate rabbitTemplate2 = new RabbitTemplate(connectionFactory);
    RabbitMqOdysseyStreamNotifier notifier2 =
        new RabbitMqOdysseyStreamNotifier(
            rabbitTemplate2, connectionFactory, "odyssey.notifications");

    CountDownLatch latch = new CountDownLatch(2);
    List<String> notifier1Received = new CopyOnWriteArrayList<>();
    List<String> notifier2Received = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          notifier1Received.add(eventId);
          latch.countDown();
        });
    notifier2.subscribe(
        (streamKey, eventId) -> {
          notifier2Received.add(eventId);
          latch.countDown();
        });

    notifier.start();
    notifier2.start();

    try {
      notifier.notify("odyssey:channel:test", "42-0");

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(notifier1Received).containsExactly("42-0");
      assertThat(notifier2Received).containsExactly("42-0");
    } finally {
      notifier2.stop();
    }
  }
}
