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
 * Functional interface for receiving event notifications from an {@link OdysseyStreamNotifier}.
 * Implementations are invoked on the notifier's managed thread(s) and should return quickly without
 * performing I/O. Typically, the handler looks up local subscribers for the given stream key and
 * nudges them to read new events from the event log.
 *
 * @see OdysseyStreamNotifier#subscribe(NotificationHandler)
 */
@FunctionalInterface
public interface NotificationHandler {

  /**
   * Called when a new event has been appended to a stream.
   *
   * @param streamKey the stream that received the new event
   * @param eventId the ID of the newly appended event
   */
  void onNotification(String streamKey, String eventId);
}
