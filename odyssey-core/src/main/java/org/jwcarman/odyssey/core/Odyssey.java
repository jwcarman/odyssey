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

import java.util.function.Consumer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Top-level facade for publishing events to and subscribing to Odyssey streams. Typical usage is to
 * inject {@code Odyssey} into a Spring {@code @RestController} and call it from request handlers.
 *
 * <p>Odyssey splits stream access into two independent sides:
 *
 * <ul>
 *   <li>Producers call {@link #publisher(String, Class)} (or one of the sugared {@link
 *       #ephemeral(Class)}, {@link #channel(String, Class)}, {@link #broadcast(String, Class)}
 *       variants) to get an {@link OdysseyPublisher}. Publishers own the journal lifecycle -- they
 *       create (or adopt) the journal eagerly at the call site and support try-with-resources for
 *       normal completion.
 *   <li>Consumers call {@link #subscribe(String, Class)}, {@link #resume(String, Class, String)},
 *       or {@link #replay(String, Class, int)} to get an {@link SseEmitter} that is already driving
 *       a virtual-thread writer loop. The starting position is part of the method name -- the
 *       returned emitter is opaque from then on.
 * </ul>
 *
 * <p>Every publisher and subscriber method has a second overload that accepts a {@link Consumer}
 * customizer. The customizer mutates a {@link PublisherConfig} or {@link SubscriberConfig}
 * directly; the library owns the builder lifecycle. Application-wide defaults live in {@link
 * PublisherCustomizer} and {@link SubscriberCustomizer} Spring beans, which run before the per-call
 * customizer so callers can still override what they care about.
 */
public interface Odyssey {

  <T> OdysseyPublisher<T> publisher(String key, Class<T> type);

  <T> OdysseyPublisher<T> publisher(
      String key, Class<T> type, Consumer<PublisherConfig> customizer);

  <T> OdysseyPublisher<T> ephemeral(Class<T> type);

  <T> OdysseyPublisher<T> ephemeral(Class<T> type, Consumer<PublisherConfig> customizer);

  <T> OdysseyPublisher<T> channel(String name, Class<T> type);

  <T> OdysseyPublisher<T> channel(String name, Class<T> type, Consumer<PublisherConfig> customizer);

  <T> OdysseyPublisher<T> broadcast(String name, Class<T> type);

  <T> OdysseyPublisher<T> broadcast(
      String name, Class<T> type, Consumer<PublisherConfig> customizer);

  <T> SseEmitter subscribe(String key, Class<T> type);

  <T> SseEmitter subscribe(String key, Class<T> type, Consumer<SubscriberConfig<T>> customizer);

  <T> SseEmitter resume(String key, Class<T> type, String lastEventId);

  <T> SseEmitter resume(
      String key, Class<T> type, String lastEventId, Consumer<SubscriberConfig<T>> customizer);

  <T> SseEmitter replay(String key, Class<T> type, int count);

  <T> SseEmitter replay(
      String key, Class<T> type, int count, Consumer<SubscriberConfig<T>> customizer);
}
