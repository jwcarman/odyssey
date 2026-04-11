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
import java.util.function.Consumer;

/**
 * Mutable configuration handed to a subscriber customizer lambda. Setters return {@code this} for
 * chaining inside the lambda.
 *
 * <p>The subscription's <strong>starting position</strong> is deliberately not on this interface --
 * it is encoded in which {@code Odyssey} method the caller chose:
 *
 * <ul>
 *   <li>{@code odyssey.subscribe(...)} -- live tail from the current head
 *   <li>{@code odyssey.resume(..., lastEventId)} -- resume after a known entry id
 *   <li>{@code odyssey.replay(..., count)} -- replay the last {@code count} entries first
 * </ul>
 *
 * <p>What this config controls:
 *
 * <ul>
 *   <li>{@link #timeout(Duration)} -- SSE emitter timeout. Zero means no timeout.
 *   <li>{@link #keepAliveInterval(Duration)} -- how often to emit an SSE keep-alive comment when no
 *       new entries are available.
 *   <li>{@link #mapper(SseEventMapper)} -- converter from {@link DeliveredEvent} to an SSE frame.
 *       Defaults to {@link SseEventMapper#defaultMapper} (typed JSON via Jackson).
 *   <li>{@link #onCompleted(Runnable)} / {@link #onExpired(Runnable)} / {@link
 *       #onDeleted(Runnable)} / {@link #onErrored(Consumer)} -- side-effect callbacks that fire
 *       when the subscription reaches a terminal state. Unrelated to the mapper's {@code
 *       terminal()} hook, which emits SSE frames; these are plain callbacks for metrics and
 *       logging.
 * </ul>
 *
 * @param <T> the typed payload delivered by the subscription
 */
public interface SubscriberConfig<T> {

  SubscriberConfig<T> timeout(Duration timeout);

  SubscriberConfig<T> keepAliveInterval(Duration interval);

  SubscriberConfig<T> mapper(SseEventMapper<T> mapper);

  SubscriberConfig<T> onCompleted(Runnable action);

  SubscriberConfig<T> onExpired(Runnable action);

  SubscriberConfig<T> onDeleted(Runnable action);

  SubscriberConfig<T> onErrored(Consumer<Throwable> action);
}
