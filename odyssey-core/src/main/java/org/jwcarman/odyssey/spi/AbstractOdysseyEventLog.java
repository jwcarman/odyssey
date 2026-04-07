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

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/**
 * Base class for {@link OdysseyEventLog} implementations. Provides stream key generation using
 * configurable prefixes and a cluster-safe, time-ordered event ID generator based on UUID v7.
 *
 * <p>All event log implementations should extend this class rather than implementing {@link
 * OdysseyEventLog} directly. Subclasses need only implement the storage operations: {@link
 * #append(String, OdysseyEvent)}, {@link #readAfter(String, String)}, {@link #readLast(String,
 * int)}, and {@link #delete(String)}.
 *
 * <p>Example subclass:
 *
 * <pre>{@code
 * public class MyEventLog extends AbstractOdysseyEventLog {
 *     public MyEventLog(String ephemeralPrefix, String channelPrefix, String broadcastPrefix) {
 *         super(ephemeralPrefix, channelPrefix, broadcastPrefix);
 *     }
 *
 *     @Override
 *     public String append(String streamKey, OdysseyEvent event) {
 *         String eventId = generateEventId();
 *         // store the event...
 *         return eventId;
 *     }
 *     // ... other methods
 * }
 * }</pre>
 */
public abstract class AbstractOdysseyEventLog implements OdysseyEventLog {

  private static final TimeBasedEpochGenerator UUID_GENERATOR =
      Generators.timeBasedEpochGenerator();

  private final String ephemeralPrefix;
  private final String channelPrefix;
  private final String broadcastPrefix;

  /**
   * Creates an event log with the given key prefixes. The prefixes are prepended to stream names to
   * form full stream keys (e.g., prefix {@code "odyssey:channel:"} + name {@code "user:123"}).
   *
   * @param ephemeralPrefix prefix for ephemeral stream keys
   * @param channelPrefix prefix for channel stream keys
   * @param broadcastPrefix prefix for broadcast stream keys
   */
  protected AbstractOdysseyEventLog(
      String ephemeralPrefix, String channelPrefix, String broadcastPrefix) {
    this.ephemeralPrefix = ephemeralPrefix;
    this.channelPrefix = channelPrefix;
    this.broadcastPrefix = broadcastPrefix;
  }

  @Override
  public String ephemeralKey() {
    return ephemeralPrefix + UUID.randomUUID();
  }

  @Override
  public String channelKey(String name) {
    return channelPrefix + name;
  }

  @Override
  public String broadcastKey(String name) {
    return broadcastPrefix + name;
  }

  /**
   * Generates a time-ordered, cluster-safe event ID using UUID v7. The generated IDs are
   * lexicographically sortable by time, making them suitable for cursor-based pagination.
   *
   * @return a new UUID v7 string
   */
  protected String generateEventId() {
    return UUID_GENERATOR.generate().toString();
  }

  /**
   * Returns the configured ephemeral key prefix.
   *
   * @return the ephemeral prefix
   */
  protected String ephemeralPrefix() {
    return ephemeralPrefix;
  }

  /**
   * Returns the configured channel key prefix.
   *
   * @return the channel prefix
   */
  protected String channelPrefix() {
    return channelPrefix;
  }

  /**
   * Returns the configured broadcast key prefix.
   *
   * @return the broadcast prefix
   */
  protected String broadcastPrefix() {
    return broadcastPrefix;
  }
}
