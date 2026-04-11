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

import java.time.Duration;

/**
 * Typed producer for events of type {@code T}. A publisher owns the lifecycle of one Odyssey stream
 * -- it creates the underlying journal (or adopts an existing one) when the {@link Odyssey} facade
 * hands it back, and releases it via {@link #close()} or {@link #delete()}.
 *
 * <p>Publishers are immutable after construction: the TTLs configured via {@link PublisherConfig}
 * are frozen at the call site. To change TTLs, construct a new publisher.
 *
 * <p>Implements {@link AutoCloseable} so try-with-resources can mark a stream as completed cleanly:
 *
 * <pre>{@code
 * try (var pub = odyssey.channel("orders:42", OrderEvent.class)) {
 *   pub.publish("created", OrderEvent.created(order));
 *   pub.publish("paid", OrderEvent.paid(order));
 * } // -> journal.complete(retentionTtl)
 * }</pre>
 *
 * @param <T> the typed payload the publisher accepts on {@link #publish(Object)}
 */
public interface OdysseyPublisher<T> extends AutoCloseable {

  String publish(T data);

  String publish(String eventType, T data);

  @Override
  void close();

  void close(Duration retentionTtl);

  void delete();

  String key();
}
