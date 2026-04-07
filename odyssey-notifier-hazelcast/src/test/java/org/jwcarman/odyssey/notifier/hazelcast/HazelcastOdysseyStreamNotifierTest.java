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
package org.jwcarman.odyssey.notifier.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HazelcastOdysseyStreamNotifierTest {

  private HazelcastInstance hazelcastInstance;
  private HazelcastOdysseyStreamNotifier notifier;

  @BeforeEach
  void setUp() {
    Config config = new Config();
    config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
    hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    notifier = new HazelcastOdysseyStreamNotifier(hazelcastInstance, "odyssey-notifications");
  }

  @AfterEach
  void tearDown() {
    if (notifier.isRunning()) {
      notifier.stop();
    }
    hazelcastInstance.shutdown();
  }

  @Test
  void notifyDeliversMessageToSubscribedHandler() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> received = new ArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          received.add(streamKey + ":" + eventId);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:channel:test", "42-0");

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly("odyssey:channel:test:42-0");
  }

  @Test
  void streamKeyAndEventIdAreParsedCorrectly() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> streamKeys = new ArrayList<>();
    List<String> eventIds = new ArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          streamKeys.add(streamKey);
          eventIds.add(eventId);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:ephemeral:abc-123", "1-0");

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(streamKeys).containsExactly("odyssey:ephemeral:abc-123");
    assertThat(eventIds).containsExactly("1-0");
  }

  @Test
  void multipleHandlersAllReceiveNotifications() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    List<String> handler1Received = new ArrayList<>();
    List<String> handler2Received = new ArrayList<>();

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
  void startMakesNotifierRunning() {
    assertThat(notifier.isRunning()).isFalse();
    notifier.start();
    assertThat(notifier.isRunning()).isTrue();
  }

  @Test
  void stopMakesNotifierNotRunning() {
    notifier.start();
    notifier.stop();
    assertThat(notifier.isRunning()).isFalse();
  }

  @Test
  void stopWhenNotRunningIsNoOp() {
    notifier.stop();
    assertThat(notifier.isRunning()).isFalse();
  }

  @Test
  void messagesAfterStopAreNotDelivered() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> received = new ArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          received.add(eventId);
          latch.countDown();
        });
    notifier.start();
    notifier.stop();

    notifier.notify("odyssey:channel:test", "99-0");

    assertThat(latch.await(1, TimeUnit.SECONDS)).isFalse();
    assertThat(received).isEmpty();
  }

  @Test
  void eventIdContainingPipeIsParsedCorrectly() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> eventIds = new ArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          eventIds.add(eventId);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:channel:test", "data|with|pipes");

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(eventIds).containsExactly("data|with|pipes");
  }
}
