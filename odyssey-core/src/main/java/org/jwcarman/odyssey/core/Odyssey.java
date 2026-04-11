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
