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
