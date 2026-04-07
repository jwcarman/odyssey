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

import java.util.stream.Stream;
import org.jwcarman.odyssey.core.OdysseyEvent;

/**
 * SPI for persistent event storage. Implementations are responsible for storing, retrieving, and
 * managing the lifecycle of events within a stream.
 *
 * <p>Implementations should extend {@link AbstractOdysseyEventLog} rather than implementing this
 * interface directly. The abstract base class provides key generation logic and a cluster-safe,
 * time-ordered event ID generator.
 *
 * <p>Each implementation is expected to handle its own trimming and TTL management. Events that
 * have been trimmed may not appear in {@link #readAfter(String, String)} results; callers handle
 * this gracefully by starting from the earliest available event.
 *
 * @see AbstractOdysseyEventLog
 */
public interface OdysseyEventLog {

  /**
   * Generates a new unique key for an ephemeral stream. Each call produces a different key.
   *
   * @return a unique ephemeral stream key
   */
  String ephemeralKey();

  /**
   * Returns the stream key for a named channel.
   *
   * @param name the channel name (e.g., {@code "user:123"})
   * @return the channel stream key
   */
  String channelKey(String name);

  /**
   * Returns the stream key for a named broadcast topic.
   *
   * @param name the broadcast name (e.g., {@code "announcements"})
   * @return the broadcast stream key
   */
  String broadcastKey(String name);

  /**
   * Appends an event to the stream identified by {@code streamKey}. The implementation assigns an
   * event ID and persists all event fields including metadata.
   *
   * @param streamKey the stream to append to
   * @param event the event to store (the {@code id} field may be {@code null}; implementations
   *     generate it)
   * @return the assigned event ID
   */
  String append(String streamKey, OdysseyEvent event);

  /**
   * Reads all events from the stream that were appended strictly after the given ID, in
   * chronological order. If {@code lastId} is no longer present (e.g., trimmed), returns events
   * from the earliest available position.
   *
   * <p>The returned stream may be lazy; callers should consume it promptly.
   *
   * @param streamKey the stream to read from
   * @param lastId the cursor position (exclusive); events after this ID are returned
   * @return a stream of events in chronological order
   */
  Stream<OdysseyEvent> readAfter(String streamKey, String lastId);

  /**
   * Reads the last {@code count} events from the stream in chronological order (oldest first). If
   * the stream contains fewer than {@code count} events, all available events are returned.
   *
   * @param streamKey the stream to read from
   * @param count the maximum number of recent events to return
   * @return a stream of the most recent events in chronological order
   */
  Stream<OdysseyEvent> readLast(String streamKey, int count);

  /**
   * Deletes the stream and all of its events. After this call, the stream key may be reused.
   *
   * @param streamKey the stream to delete
   */
  void delete(String streamKey);
}
