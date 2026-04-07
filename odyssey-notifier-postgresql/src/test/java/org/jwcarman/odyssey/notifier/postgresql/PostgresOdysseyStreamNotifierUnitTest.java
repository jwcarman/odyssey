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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.spi.NotificationHandler;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PostgresOdysseyStreamNotifierUnitTest {

  @Mock private JdbcTemplate jdbcTemplate;

  @Mock private DataSource dataSource;

  @Test
  void validChannelNameDoesNotThrow() {
    assertDoesNotThrow(
        () -> new PostgresOdysseyStreamNotifier(jdbcTemplate, dataSource, "odyssey_notify", 100));
  }

  @Test
  void validChannelNameWithLettersOnly() {
    assertDoesNotThrow(
        () -> new PostgresOdysseyStreamNotifier(jdbcTemplate, dataSource, "odyssey", 100));
  }

  @Test
  void validChannelNameStartingWithUnderscore() {
    assertDoesNotThrow(
        () -> new PostgresOdysseyStreamNotifier(jdbcTemplate, dataSource, "_test", 100));
  }

  @Test
  void invalidChannelNameWithHyphen() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostgresOdysseyStreamNotifier(jdbcTemplate, dataSource, "odyssey-notify", 100));
  }

  @Test
  void invalidChannelNameStartingWithNumber() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostgresOdysseyStreamNotifier(jdbcTemplate, dataSource, "123abc", 100));
  }

  @Test
  void invalidChannelNameEmpty() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostgresOdysseyStreamNotifier(jdbcTemplate, dataSource, "", 100));
  }

  @Test
  void invalidChannelNameWithSpecialChars() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostgresOdysseyStreamNotifier(jdbcTemplate, dataSource, "foo@bar", 100));
  }

  @Test
  void dispatchNotificationWithValidPayload() {
    PostgresOdysseyStreamNotifier notifier =
        new PostgresOdysseyStreamNotifier(jdbcTemplate, dataSource, "test_channel", 100);
    NotificationHandler handler = org.mockito.Mockito.mock(NotificationHandler.class);
    notifier.subscribe(handler);

    notifier.dispatchNotification("stream:test|42");

    verify(handler).onNotification("stream:test", "42");
  }

  @Test
  void dispatchNotificationWithMalformedPayload() {
    PostgresOdysseyStreamNotifier notifier =
        new PostgresOdysseyStreamNotifier(jdbcTemplate, dataSource, "test_channel", 100);
    NotificationHandler handler = org.mockito.Mockito.mock(NotificationHandler.class);
    notifier.subscribe(handler);

    notifier.dispatchNotification("no-delimiter");

    verifyNoInteractions(handler);
  }

  @Test
  void dispatchNotificationWithMultipleHandlers() {
    PostgresOdysseyStreamNotifier notifier =
        new PostgresOdysseyStreamNotifier(jdbcTemplate, dataSource, "test_channel", 100);
    NotificationHandler handler1 = org.mockito.Mockito.mock(NotificationHandler.class);
    NotificationHandler handler2 = org.mockito.Mockito.mock(NotificationHandler.class);
    notifier.subscribe(handler1);
    notifier.subscribe(handler2);

    notifier.dispatchNotification("stream:test|42");

    verify(handler1).onNotification("stream:test", "42");
    verify(handler2).onNotification("stream:test", "42");
  }
}
