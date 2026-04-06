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
package org.jwcarman.odyssey.notifier.redis;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jwcarman.odyssey.spi.NotificationHandler;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.context.SmartLifecycle;

public class RedisOdysseyStreamNotifier extends RedisPubSubAdapter<String, String>
    implements OdysseyStreamNotifier, SmartLifecycle {

  private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
  private final RedisPubSubCommands<String, String> pubSubCommands;
  private final io.lettuce.core.api.sync.RedisCommands<String, String> sharedCommands;
  private final String notifyPrefix;
  private final String subscribePattern;
  private final List<NotificationHandler> handlers = new CopyOnWriteArrayList<>();

  private volatile boolean running;

  public RedisOdysseyStreamNotifier(
      StatefulRedisPubSubConnection<String, String> pubSubConnection,
      io.lettuce.core.api.sync.RedisCommands<String, String> sharedCommands,
      String channelPrefix) {
    this.pubSubConnection = pubSubConnection;
    this.pubSubCommands = pubSubConnection.sync();
    this.sharedCommands = sharedCommands;
    this.notifyPrefix = channelPrefix;
    this.subscribePattern = channelPrefix + "*";
  }

  @Override
  public void notify(String streamKey, String eventId) {
    sharedCommands.publish(notifyPrefix + streamKey, eventId);
  }

  @Override
  public void subscribe(NotificationHandler handler) {
    handlers.add(handler);
  }

  @Override
  public void message(String channel, String message) {
    // Not used — we only use pattern subscriptions
  }

  @Override
  public void message(String pattern, String channel, String message) {
    String streamKey = channel.substring(notifyPrefix.length());
    for (NotificationHandler handler : handlers) {
      handler.onNotification(streamKey, message);
    }
  }

  @Override
  public void start() {
    pubSubConnection.addListener(this);
    pubSubCommands.psubscribe(subscribePattern);
    running = true;
  }

  @Override
  public void stop() {
    running = false;
    pubSubCommands.punsubscribe(subscribePattern);
    pubSubConnection.removeListener(this);
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
