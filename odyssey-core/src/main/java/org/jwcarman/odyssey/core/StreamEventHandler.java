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
 * Callback interface for consuming stream events without an {@link
 * org.springframework.web.servlet.mvc.method.annotation.SseEmitter}. Implement this interface to
 * receive events programmatically rather than through an SSE connection.
 *
 * <p>Implementations must be thread-safe, as callbacks may be invoked from virtual threads managed
 * by the OdySSEy engine.
 */
public interface StreamEventHandler {

  /**
   * Called when a new event is received from the stream.
   *
   * @param event the event that was published to the stream
   */
  void onEvent(OdysseyEvent event);

  /**
   * Called when no events have been received within the configured keep-alive interval. This
   * corresponds to the SSE keep-alive comment sent to emitter-based subscribers.
   */
  void onKeepAlive();

  /**
   * Called when the stream is gracefully closed via {@link OdysseyStream#close()}. After this
   * callback, no further events will be delivered to this handler.
   */
  void onComplete();

  /**
   * Called when an error occurs during event delivery.
   *
   * @param e the exception that occurred
   */
  void onError(Exception e);
}
