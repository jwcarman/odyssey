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
package org.jwcarman.odyssey.memory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jwcarman.odyssey.spi.NotificationHandler;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;

/**
 * In-memory implementation of {@link OdysseyStreamNotifier} that delivers notifications directly
 * within the same JVM. Suitable for single-node environments and testing only; does not support
 * cross-node notification.
 */
public class InMemoryOdysseyStreamNotifier implements OdysseyStreamNotifier {

  /** Creates a new in-memory stream notifier. */
  public InMemoryOdysseyStreamNotifier() {}

  private final List<NotificationHandler> handlers = new CopyOnWriteArrayList<>();

  @Override
  public void notify(String streamKey, String eventId) {
    for (NotificationHandler handler : handlers) {
      handler.onNotification(streamKey, eventId);
    }
  }

  @Override
  public void subscribe(NotificationHandler handler) {
    handlers.add(handler);
  }
}
