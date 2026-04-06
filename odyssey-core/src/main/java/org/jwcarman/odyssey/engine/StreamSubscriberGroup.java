package org.jwcarman.odyssey.engine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class StreamSubscriberGroup {

  private final List<StreamSubscriber> subscribers = new CopyOnWriteArrayList<>();

  void addSubscriber(StreamSubscriber subscriber) {
    subscribers.add(subscriber);
  }

  void removeSubscriber(StreamSubscriber subscriber) {
    subscribers.remove(subscriber);
  }

  void nudgeAll() {
    for (StreamSubscriber subscriber : subscribers) {
      subscriber.nudge();
    }
  }

  void shutdown() {
    for (StreamSubscriber subscriber : subscribers) {
      subscriber.closeGracefully();
    }
  }

  void shutdownImmediately() {
    for (StreamSubscriber subscriber : subscribers) {
      subscriber.closeImmediately();
    }
  }

  boolean hasSubscribers() {
    return !subscribers.isEmpty();
  }
}
