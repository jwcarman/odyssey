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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.jwcarman.odyssey.spi.NotificationHandler;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.context.SmartLifecycle;

public class HazelcastOdysseyStreamNotifier implements OdysseyStreamNotifier, SmartLifecycle {

  private final HazelcastInstance hazelcastInstance;
  private final String topicName;
  private final List<NotificationHandler> handlers = new CopyOnWriteArrayList<>();
  private final AtomicReference<UUID> registrationId = new AtomicReference<>();

  public HazelcastOdysseyStreamNotifier(HazelcastInstance hazelcastInstance, String topicName) {
    this.hazelcastInstance = hazelcastInstance;
    this.topicName = topicName;
  }

  @Override
  public void notify(String streamKey, String eventId) {
    ITopic<String> topic = hazelcastInstance.getTopic(topicName);
    topic.publish(streamKey + "|" + eventId);
  }

  @Override
  public void subscribe(NotificationHandler handler) {
    handlers.add(handler);
  }

  @Override
  public void start() {
    ITopic<String> topic = hazelcastInstance.getTopic(topicName);
    UUID id =
        topic.addMessageListener(
            message -> {
              String payload = message.getMessageObject();
              int separatorIndex = payload.indexOf('|');
              String streamKey = payload.substring(0, separatorIndex);
              String eventId = payload.substring(separatorIndex + 1);
              for (NotificationHandler handler : handlers) {
                handler.onNotification(streamKey, eventId);
              }
            });
    registrationId.set(id);
  }

  @Override
  public void stop() {
    UUID id = registrationId.getAndSet(null);
    if (id != null) {
      ITopic<String> topic = hazelcastInstance.getTopic(topicName);
      topic.removeMessageListener(id);
    }
  }

  @Override
  public boolean isRunning() {
    return registrationId.get() != null;
  }
}
