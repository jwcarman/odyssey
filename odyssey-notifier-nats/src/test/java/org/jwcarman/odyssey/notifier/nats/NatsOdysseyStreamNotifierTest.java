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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NatsOdysseyStreamNotifierTest {

  @Mock private Connection connection;
  @Mock private Dispatcher dispatcher;

  private NatsOdysseyStreamNotifier notifier;

  @BeforeEach
  void setUp() {
    notifier = new NatsOdysseyStreamNotifier(connection, "odyssey.notify.");
  }

  @Test
  void notifyPublishesEventIdToCorrectSubject() {
    notifier.notify("odyssey:channel:my-channel", "1712404800000-0");

    verify(connection)
        .publish(
            "odyssey.notify.odyssey.channel.my-channel",
            "1712404800000-0".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void streamKeyColonsAreConvertedToDotsInSubject() {
    notifier.notify("odyssey:ephemeral:abc-123", "42-0");

    verify(connection)
        .publish(
            "odyssey.notify.odyssey.ephemeral.abc-123", "42-0".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void startCreatesDispatcherAndSubscribes() {
    when(connection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);

    notifier.start();

    verify(connection).createDispatcher(any(MessageHandler.class));
    verify(dispatcher).subscribe("odyssey.notify.>");
    assertThat(notifier.isRunning()).isTrue();
  }

  @Test
  void stopClosesDispatcher() {
    when(connection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);

    notifier.start();
    notifier.stop();

    verify(connection).closeDispatcher(dispatcher);
    assertThat(notifier.isRunning()).isFalse();
  }

  @Test
  void subscribeDispatchesIncomingMessagesToHandler() throws InterruptedException {
    ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
    when(connection.createDispatcher(handlerCaptor.capture())).thenReturn(dispatcher);

    List<String> received = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> received.add(streamKey + ":" + eventId));
    notifier.start();

    Message message = mockMessage("odyssey.notify.odyssey.channel.test", "42-0");
    handlerCaptor.getValue().onMessage(message);

    assertThat(received).containsExactly("odyssey:channel:test:42-0");
  }

  @Test
  void streamKeyIsExtractedByStrippingPrefixAndConvertingDotsToColons()
      throws InterruptedException {
    ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
    when(connection.createDispatcher(handlerCaptor.capture())).thenReturn(dispatcher);

    List<String> streamKeys = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> streamKeys.add(streamKey));
    notifier.start();

    Message message = mockMessage("odyssey.notify.odyssey.ephemeral.abc-123", "1-0");
    handlerCaptor.getValue().onMessage(message);

    assertThat(streamKeys).containsExactly("odyssey:ephemeral:abc-123");
  }

  @Test
  void multipleHandlersAllReceiveNotifications() throws InterruptedException {
    ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
    when(connection.createDispatcher(handlerCaptor.capture())).thenReturn(dispatcher);

    List<String> handler1Received = new ArrayList<>();
    List<String> handler2Received = new ArrayList<>();

    notifier.subscribe((streamKey, eventId) -> handler1Received.add(eventId));
    notifier.subscribe((streamKey, eventId) -> handler2Received.add(eventId));
    notifier.start();

    Message message = mockMessage("odyssey.notify.odyssey.broadcast.news", "5-0");
    handlerCaptor.getValue().onMessage(message);

    assertThat(handler1Received).containsExactly("5-0");
    assertThat(handler2Received).containsExactly("5-0");
  }

  private static Message mockMessage(String subject, String data) {
    Message message = mock(Message.class);
    when(message.getSubject()).thenReturn(subject);
    when(message.getData()).thenReturn(data.getBytes(StandardCharsets.UTF_8));
    return message;
  }
}
