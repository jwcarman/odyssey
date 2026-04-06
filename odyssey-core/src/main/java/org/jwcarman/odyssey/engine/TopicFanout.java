package org.jwcarman.odyssey.engine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class TopicFanout {

  private final List<SubscriberOutbox> subscribers = new CopyOnWriteArrayList<>();

  void addSubscriber(SubscriberOutbox outbox) {
    subscribers.add(outbox);
  }

  void removeSubscriber(SubscriberOutbox outbox) {
    subscribers.remove(outbox);
  }

  void nudgeAll() {
    for (SubscriberOutbox outbox : subscribers) {
      outbox.nudge();
    }
  }

  void shutdown() {
    for (SubscriberOutbox outbox : subscribers) {
      outbox.closeGracefully();
    }
  }

  void shutdownImmediately() {
    for (SubscriberOutbox outbox : subscribers) {
      outbox.closeImmediately();
    }
  }

  boolean hasSubscribers() {
    return !subscribers.isEmpty();
  }
}
