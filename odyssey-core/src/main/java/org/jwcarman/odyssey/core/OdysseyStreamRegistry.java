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
package org.jwcarman.odyssey.core;

/**
 * Factory for creating and retrieving {@link OdysseyStream} instances. Provides three stream types
 * optimized for different access patterns:
 *
 * <ul>
 *   <li><strong>Ephemeral</strong> — short-lived, one-to-one streams (e.g., MCP tool calls)
 *   <li><strong>Channel</strong> — per-user or per-entity streams with medium TTL
 *   <li><strong>Broadcast</strong> — system-wide streams with many subscribers
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * // Create an ephemeral stream for an MCP tool call
 * OdysseyStream stream = registry.ephemeral();
 * SseEmitter emitter = stream.subscribe();
 *
 * // Reconnect to an existing stream by key
 * OdysseyStream existing = registry.stream(streamKey);
 * SseEmitter emitter = existing.resumeAfter(lastEventId);
 * }</pre>
 *
 * <p>Channel and broadcast streams are cached by name: calling {@code channel("user:123")} multiple
 * times returns the same {@link OdysseyStream} instance.
 *
 * @see OdysseyStream
 */
public interface OdysseyStreamRegistry {

  /**
   * Creates a new ephemeral stream with an auto-generated key. Ephemeral streams have a short TTL
   * and are intended for request-scoped exchanges such as MCP tool calls.
   *
   * @return a new ephemeral {@link OdysseyStream}
   */
  OdysseyStream ephemeral();

  /**
   * Returns a channel stream for the given name, creating it if necessary. Channel streams are
   * cached by name and intended for per-user or per-entity delivery with a medium TTL. Calling this
   * method multiple times with the same name returns the same stream instance.
   *
   * @param name the channel name (e.g., {@code "user:123"})
   * @return the {@link OdysseyStream} for the given channel name
   */
  OdysseyStream channel(String name);

  /**
   * Returns a broadcast stream for the given name, creating it if necessary. Broadcast streams are
   * cached by name and intended for system-wide announcements with many subscribers and a long TTL.
   * Calling this method multiple times with the same name returns the same stream instance.
   *
   * @param name the broadcast topic name (e.g., {@code "announcements"})
   * @return the {@link OdysseyStream} for the given broadcast name
   */
  OdysseyStream broadcast(String name);

  /**
   * Looks up an existing stream by its full stream key. This is the primary mechanism for SSE
   * reconnect: the client provides the stream key it was previously connected to, and the
   * application uses this method to retrieve the stream and call {@link
   * OdysseyStream#resumeAfter(String)}.
   *
   * @param streamKey the full stream key (e.g., {@code "odyssey:channel:user:123"})
   * @return the {@link OdysseyStream} for the given key
   */
  OdysseyStream stream(String streamKey);
}
