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

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jwcarman.odyssey.spi.NotificationHandler;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.context.SmartLifecycle;

public class NatsOdysseyStreamNotifier implements OdysseyStreamNotifier, SmartLifecycle {

  private final Connection connection;
  private final String subjectPrefix;
  private final List<NotificationHandler> handlers = new CopyOnWriteArrayList<>();

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicReference<Dispatcher> dispatcher = new AtomicReference<>();

  public NatsOdysseyStreamNotifier(Connection connection, String subjectPrefix) {
    this.connection = connection;
    this.subjectPrefix = subjectPrefix;
  }

  @Override
  public void notify(String streamKey, String eventId) {
    String subject = toSubject(streamKey);
    connection.publish(subject, eventId.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public void subscribe(NotificationHandler handler) {
    handlers.add(handler);
  }

  @Override
  public void start() {
    Dispatcher d = connection.createDispatcher(this::handleMessage);
    d.subscribe(subjectPrefix + ">");
    dispatcher.set(d);
    running.set(true);
  }

  @Override
  public void stop() {
    running.set(false);
    Dispatcher d = dispatcher.getAndSet(null);
    if (d != null) {
      connection.closeDispatcher(d);
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  private void handleMessage(Message message) {
    String streamKey = toStreamKey(message.getSubject());
    String eventId = new String(message.getData(), StandardCharsets.UTF_8);
    for (NotificationHandler handler : handlers) {
      handler.onNotification(streamKey, eventId);
    }
  }

  private String toSubject(String streamKey) {
    return subjectPrefix + streamKey.replace(':', '.');
  }

  private String toStreamKey(String subject) {
    return subject.substring(subjectPrefix.length()).replace('.', ':');
  }
}
