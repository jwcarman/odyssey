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
package org.jwcarman.odyssey.notifier.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresOdysseyStreamNotifierTest {

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private PostgresOdysseyStreamNotifier notifier;
  private DataSource dataSource;

  @BeforeEach
  void setUp() {
    dataSource = createDataSource();
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    notifier = new PostgresOdysseyStreamNotifier(jdbcTemplate, dataSource, "odyssey_notify", 100);
  }

  @AfterEach
  void tearDown() {
    if (notifier.isRunning()) {
      notifier.stop();
    }
  }

  @Test
  void notifyAndSubscribeDeliversNotification() throws Exception {
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

    // Small delay to let the LISTEN connection establish
    Thread.sleep(200);

    notifier.notify("channel:orders", "42");

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(receivedStreamKeys).containsExactly("channel:orders");
    assertThat(receivedEventIds).containsExactly("42");
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
    Thread.sleep(200);

    notifier.notify("broadcast:news", "99");

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(handler1Received).containsExactly("99");
    assertThat(handler2Received).containsExactly("99");
  }

  @Test
  void multipleNotificationsAreDeliveredInOrder() throws Exception {
    int count = 5;
    CountDownLatch latch = new CountDownLatch(count);
    List<String> received = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          received.add(eventId);
          latch.countDown();
        });

    notifier.start();
    Thread.sleep(200);

    for (int i = 0; i < count; i++) {
      notifier.notify("channel:test", String.valueOf(i));
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly("0", "1", "2", "3", "4");
  }

  @Test
  void streamKeyWithSpecialCharactersIsPreserved() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> receivedStreamKeys = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          receivedStreamKeys.add(streamKey);
          latch.countDown();
        });

    notifier.start();
    Thread.sleep(200);

    notifier.notify("ephemeral:abc-123-def", "1");

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(receivedStreamKeys).containsExactly("ephemeral:abc-123-def");
  }

  @Test
  void startAndStopLifecycle() {
    assertThat(notifier.isRunning()).isFalse();

    notifier.start();
    assertThat(notifier.isRunning()).isTrue();

    notifier.stop();
    assertThat(notifier.isRunning()).isFalse();
  }

  @Test
  void stopIsIdempotent() {
    notifier.start();
    notifier.stop();
    notifier.stop();
    assertThat(notifier.isRunning()).isFalse();
  }

  private DataSource createDataSource() {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setUrl(postgres.getJdbcUrl());
    ds.setUsername(postgres.getUsername());
    ds.setPassword(postgres.getPassword());
    return ds;
  }
}
