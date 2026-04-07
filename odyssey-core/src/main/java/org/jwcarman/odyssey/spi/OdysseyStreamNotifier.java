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
package org.jwcarman.odyssey.spi;

/**
 * SPI for cross-node notification when new events are appended to a stream. Implementations
 * decouple the event log write from subscriber wake-up, enabling clustered deployments where
 * subscribers on different nodes need to be notified of new events.
 *
 * <p>Notification is fire-and-forget: a missed notification does not cause data loss, because
 * subscriber reader threads periodically poll the event log as a safety net.
 *
 * <p>Implementations are responsible for their own thread management and connection lifecycle.
 *
 * @see NotificationHandler
 */
public interface OdysseyStreamNotifier {

  /**
   * Sends a notification that a new event has been appended to the given stream. This method should
   * return quickly and must not block on subscriber delivery. It is called after every successful
   * {@link OdysseyEventLog#append(String, org.jwcarman.odyssey.core.OdysseyEvent)}.
   *
   * @param streamKey the stream that received a new event
   * @param eventId the ID of the newly appended event
   */
  void notify(String streamKey, String eventId);

  /**
   * Registers a handler to receive notifications for all streams. Only one handler per node is
   * expected. The implementation must deliver notifications to the handler on its own managed
   * thread(s); the handler performs no I/O and returns quickly.
   *
   * @param handler the handler to invoke when a notification arrives
   */
  void subscribe(NotificationHandler handler);
}
