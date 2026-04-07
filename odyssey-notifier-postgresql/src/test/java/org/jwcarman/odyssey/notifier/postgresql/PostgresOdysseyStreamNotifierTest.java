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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
  void notifyAndSubscribeDeliversNotification() {
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

    await()
        .atMost(5, SECONDS)
        .pollInterval(100, MILLISECONDS)
        .until(
            () -> {
              notifier.notify("channel:orders", "42");
              return latch.await(200, TimeUnit.MILLISECONDS);
            });

    assertThat(receivedStreamKeys).contains("channel:orders");
    assertThat(receivedEventIds).contains("42");
  }

  @Test
  void multipleHandlersAllReceiveNotifications() {
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

    await()
        .atMost(5, SECONDS)
        .pollInterval(100, MILLISECONDS)
        .until(
            () -> {
              notifier.notify("broadcast:news", "99");
              return latch.await(200, TimeUnit.MILLISECONDS);
            });

    assertThat(handler1Received).contains("99");
    assertThat(handler2Received).contains("99");
  }

  @Test
  void multipleNotificationsAreDeliveredInOrder() {
    int count = 5;
    CountDownLatch latch = new CountDownLatch(count);
    List<String> received = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          received.add(eventId);
          latch.countDown();
        });

    notifier.start();

    await()
        .atMost(5, SECONDS)
        .pollInterval(100, MILLISECONDS)
        .until(
            () -> {
              notifier.notify("channel:test", String.valueOf(0));
              return latch.getCount() < count;
            });

    for (int i = 1; i < count; i++) {
      notifier.notify("channel:test", String.valueOf(i));
    }

    await().atMost(5, SECONDS).until(() -> latch.getCount() == 0);

    assertThat(received)
        .filteredOn(id -> List.of("0", "1", "2", "3", "4").contains(id))
        .last()
        .isEqualTo("4");
    assertThat(received).contains("0", "1", "2", "3", "4");
  }

  @Test
  void streamKeyWithSpecialCharactersIsPreserved() {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> receivedStreamKeys = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          receivedStreamKeys.add(streamKey);
          latch.countDown();
        });

    notifier.start();

    await()
        .atMost(5, SECONDS)
        .pollInterval(100, MILLISECONDS)
        .until(
            () -> {
              notifier.notify("ephemeral:abc-123-def", "1");
              return latch.await(200, TimeUnit.MILLISECONDS);
            });

    assertThat(receivedStreamKeys).contains("ephemeral:abc-123-def");
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
