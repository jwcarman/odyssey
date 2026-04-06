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

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NatsOdysseyStreamNotifierIT {

  @Container
  static GenericContainer natsContainer =
      new GenericContainer("nats:latest").withExposedPorts(4222);

  private Connection connection;
  private NatsOdysseyStreamNotifier notifier;

  @BeforeEach
  void setUp() throws Exception {
    String url = "nats://" + natsContainer.getHost() + ":" + natsContainer.getMappedPort(4222);
    Options options = new Options.Builder().server(url).build();
    connection = Nats.connect(options);
    notifier = new NatsOdysseyStreamNotifier(connection, "odyssey.notify.");
  }

  @AfterEach
  void tearDown() throws Exception {
    if (notifier.isRunning()) {
      notifier.stop();
    }
    if (connection != null) {
      connection.close();
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
  void streamKeyIsCorrectlyExtractedFromSubject() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> receivedStreamKeys = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          receivedStreamKeys.add(streamKey);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:ephemeral:abc-123", "1-0");

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(receivedStreamKeys).containsExactly("odyssey:ephemeral:abc-123");
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
}
