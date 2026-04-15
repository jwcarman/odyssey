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
 * Top-level facade for obtaining {@link OdysseyStream} handles. Inject into a Spring
 * {@code @RestController} or {@code @Component} and call {@link #stream(String, Class, TtlPolicy)}
 * to get a typed handle to a named stream.
 *
 * <h2>Broker semantics</h2>
 *
 * <p>Streams are created implicitly on first reference. The first caller for a given name
 * establishes the stream's TTL policy at the backend; later callers -- in this JVM or any other --
 * silently adopt the existing stream, and their TTL argument is ignored. This matches Kafka,
 * ActiveMQ, and NATS JetStream conventions. There is no declaration step, no "stream not found"
 * error at the Odyssey layer, and no TTL conflict to resolve.
 *
 * <h2>Flat namespace</h2>
 *
 * <p>Odyssey adds no prefixes, categories, or namespacing. The name passed to {@link
 * #stream(String, Class, TtlPolicy)} is the backend journal key verbatim. Apps that want several
 * stream kinds (e.g., short-lived task streams vs. long-lived broadcast streams) should define
 * their own {@link TtlPolicy} constants and wrap {@code stream(...)} calls in typed factory
 * methods:
 *
 * <pre>{@code
 * @Component
 * class Streams {
 *   private final Odyssey odyssey;
 *
 *   Streams(Odyssey odyssey) {
 *     this.odyssey = odyssey;
 *   }
 *
 *   OdysseyStream<Announcement> announcements() {
 *     return odyssey.stream("announcements", Announcement.class, BROADCAST);
 *   }
 *
 *   OdysseyStream<Notification> userChannel(String userId) {
 *     return odyssey.stream("user:" + userId, Notification.class, CHANNEL);
 *   }
 * }
 * }</pre>
 *
 * <h2>Handles are not cached</h2>
 *
 * <p>Each call returns a fresh handle wrapping a fresh backend handle. Callers who want to reuse a
 * single handle (typical for long-lived streams in a {@code @Component}) should cache it at the
 * application layer.
 */
public interface Odyssey {

  /**
   * Return a handle to the named stream. If the stream does not exist, it is created with the given
   * TTL policy; if it already exists, the TTL argument is ignored and the caller receives a handle
   * to the existing stream.
   *
   * @param name the caller-supplied stream name; becomes the backend journal key verbatim
   * @param type the typed payload class for publish and subscribe operations
   * @param ttl the TTL policy to apply if this call creates the stream; ignored otherwise
   * @param <T> the typed payload type
   * @return a handle to the named stream
   */
  <T> OdysseyStream<T> stream(String name, Class<T> type, TtlPolicy ttl);

  /**
   * Return a handle to the named stream using the default TTL policy from {@link
   * org.jwcarman.odyssey.autoconfigure.OdysseyProperties#defaultTtl()} if this call creates the
   * stream.
   *
   * @param name the caller-supplied stream name
   * @param type the typed payload class for publish and subscribe operations
   * @param <T> the typed payload type
   * @return a handle to the named stream
   */
  <T> OdysseyStream<T> stream(String name, Class<T> type);
}
