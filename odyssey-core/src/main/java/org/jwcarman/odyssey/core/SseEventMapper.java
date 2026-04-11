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

import java.util.Optional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * Maps delivered events and terminal stream states to SSE frames.
 *
 * <p>The {@link #map(DeliveredEvent)} method is required and is invoked for every event published
 * to the stream. The {@link #terminal(TerminalState)} method is optional; by default it returns
 * {@link Optional#empty()}, meaning no extra SSE frame is written when the stream ends. When a
 * terminal state is {@link TerminalState.Errored} and no in-band frame is emitted, the subscriber's
 * {@code SseEmitter} will be closed via {@code completeWithError(cause)}; otherwise it will be
 * closed via {@code complete()}.
 *
 * @param <T> the user's event payload type
 */
public interface SseEventMapper<T> {

  /**
   * Map a delivered event to an SSE frame. Called once per value produced by the subscription.
   *
   * @param event the delivered event
   * @return the SSE frame to send to the client
   */
  SseEmitter.SseEventBuilder map(DeliveredEvent<T> event);

  /**
   * Optionally map a terminal state to an SSE frame. Called once per subscription, just before the
   * emitter closes. Return {@link Optional#empty()} (the default) to not emit any terminal frame.
   *
   * <p>For {@link TerminalState.Errored}, returning a frame means the error has been signaled
   * in-band and the emitter will be closed normally via {@code complete()}. Returning empty for
   * {@code Errored} causes the emitter to be closed via {@code completeWithError(cause)} so Spring
   * MVC's error handling fires.
   *
   * @param state the terminal state
   * @return an optional SSE frame to emit before closing, or empty to emit nothing
   */
  default Optional<SseEmitter.SseEventBuilder> terminal(TerminalState state) {
    return Optional.empty();
  }

  /**
   * Sealed view of the four terminal conditions a subscription can reach. Mirrors Substrate's
   * terminal {@code NextResult} variants but stays inside the Odyssey type system so consumers
   * don't import Substrate directly.
   */
  sealed interface TerminalState {

    /** The underlying journal was completed naturally by its producer. */
    record Completed() implements TerminalState {}

    /** The underlying journal's inactivity or retention TTL elapsed. */
    record Expired() implements TerminalState {}

    /** The underlying journal was explicitly deleted. */
    record Deleted() implements TerminalState {}

    /**
     * A backend error terminated the subscription.
     *
     * @param cause the underlying throwable reported by the Substrate subscription
     */
    record Errored(Throwable cause) implements TerminalState {}
  }

  /**
   * Default mapper: sets the SSE event id to the delivered event's id, sets the event name to the
   * event type (if present), and serializes the typed payload to a JSON string for the SSE {@code
   * data:} field. Inherits the empty default for {@link #terminal(TerminalState)}.
   *
   * @param objectMapper Jackson mapper used to serialize the payload
   * @param <T> the event payload type
   * @return a mapper for typed events
   */
  static <T> SseEventMapper<T> defaultMapper(ObjectMapper objectMapper) {
    return event -> {
      SseEmitter.SseEventBuilder builder =
          SseEmitter.event().id(event.id()).data(objectMapper.writeValueAsString(event.data()));
      if (event.eventType() != null) {
        builder.name(event.eventType());
      }
      return builder;
    };
  }
}
