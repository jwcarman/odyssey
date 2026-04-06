package org.jwcarman.odyssey.redis;

import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PubSubNotificationListener implements RedisPubSubListener<String, String> {

  private final ConcurrentMap<String, TopicFanout> fanouts = new ConcurrentHashMap<>();
  private final StatefulRedisPubSubConnection<String, String> connection;
  private final String streamPrefix;
  private final String notifyPrefix;
  private Thread listenerThread;

  public PubSubNotificationListener(
      StatefulRedisPubSubConnection<String, String> connection, String streamPrefix) {
    this.connection = connection;
    this.streamPrefix = streamPrefix;
    this.notifyPrefix = streamPrefix + "notify:";
  }

  public void start() {
    listenerThread =
        Thread.ofVirtual()
            .name("odyssey-pubsub-listener")
            .start(
                () -> {
                  connection.addListener(this);
                  connection.sync().psubscribe(notifyPrefix + "*");
                });
  }

  public void stop() {
    connection.sync().punsubscribe(notifyPrefix + "*");
    connection.removeListener(this);
    if (listenerThread != null) {
      listenerThread.interrupt();
    }
  }

  void registerFanout(String streamKey, TopicFanout fanout) {
    fanouts.put(streamKey, fanout);
  }

  void unregisterFanout(String streamKey) {
    fanouts.remove(streamKey);
  }

  String extractStreamKey(String channel) {
    return channel.substring(notifyPrefix.length());
  }

  @Override
  public void message(String channel, String message) {
    // Not used for pattern subscriptions
  }

  @Override
  public void message(String pattern, String channel, String message) {
    String streamKey = extractStreamKey(channel);
    TopicFanout fanout = fanouts.get(streamKey);
    if (fanout != null && fanout.hasSubscribers()) {
      fanout.nudgeAll();
    }
  }

  @Override
  public void subscribed(String channel, long count) {
    // No action needed
  }

  @Override
  public void psubscribed(String pattern, long count) {
    // No action needed
  }

  @Override
  public void unsubscribed(String channel, long count) {
    // No action needed
  }

  @Override
  public void punsubscribed(String pattern, long count) {
    // No action needed
  }
}
