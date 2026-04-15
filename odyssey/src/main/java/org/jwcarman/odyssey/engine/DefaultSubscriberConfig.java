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
package org.jwcarman.odyssey.engine;

import java.time.Duration;
import java.util.function.Consumer;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.odyssey.core.SubscriberConfig;

class DefaultSubscriberConfig<T> implements SubscriberConfig<T> {

  private Duration timeout = Duration.ZERO;
  private Duration keepAliveInterval = Duration.ofSeconds(30);
  private SseEventMapper<T> mapper;
  private Runnable onCompleted = () -> {};
  private Runnable onExpired = () -> {};
  private Runnable onDeleted = () -> {};
  private Consumer<Throwable> onErrored = t -> {};
  private Runnable onCancelled = () -> {};
  private SubscriberConfig.SubscribeHook onSubscribe = e -> {};

  DefaultSubscriberConfig(SseEventMapper<T> defaultMapper) {
    this.mapper = defaultMapper;
  }

  @Override
  public SubscriberConfig<T> timeout(Duration timeout) {
    this.timeout = timeout;
    return this;
  }

  @Override
  public SubscriberConfig<T> keepAliveInterval(Duration interval) {
    this.keepAliveInterval = interval;
    return this;
  }

  @Override
  public SubscriberConfig<T> mapper(SseEventMapper<T> mapper) {
    this.mapper = mapper;
    return this;
  }

  @Override
  public SubscriberConfig<T> onCompleted(Runnable action) {
    this.onCompleted = action;
    return this;
  }

  @Override
  public SubscriberConfig<T> onExpired(Runnable action) {
    this.onExpired = action;
    return this;
  }

  @Override
  public SubscriberConfig<T> onDeleted(Runnable action) {
    this.onDeleted = action;
    return this;
  }

  @Override
  public SubscriberConfig<T> onErrored(Consumer<Throwable> action) {
    this.onErrored = action;
    return this;
  }

  @Override
  public SubscriberConfig<T> onCancelled(Runnable action) {
    this.onCancelled = action;
    return this;
  }

  @Override
  public SubscriberConfig<T> onSubscribe(SubscriberConfig.SubscribeHook action) {
    this.onSubscribe = action;
    return this;
  }

  SubscriberConfig.SubscribeHook onSubscribe() {
    return onSubscribe;
  }

  Duration timeout() {
    return timeout;
  }

  Duration keepAliveInterval() {
    return keepAliveInterval;
  }

  SseEventMapper<T> mapper() {
    return mapper;
  }

  Runnable onCompleted() {
    return onCompleted;
  }

  Runnable onExpired() {
    return onExpired;
  }

  Runnable onDeleted() {
    return onDeleted;
  }

  Consumer<Throwable> onErrored() {
    return onErrored;
  }

  Runnable onCancelled() {
    return onCancelled;
  }
}
