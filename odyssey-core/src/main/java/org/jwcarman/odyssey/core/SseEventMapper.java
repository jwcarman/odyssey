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

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Maps an {@link OdysseyEvent} to an SSE event for delivery to the client. Implementations control
 * how event IDs, data, and event types are written to the SSE output.
 *
 * <p>The default mapper sets the {@code id:} field from the event ID, the {@code data:} field from
 * the payload, and the {@code event:} field from the event type (if non-null).
 */
@FunctionalInterface
public interface SseEventMapper {

  /**
   * Maps an Odyssey event to an SSE event builder.
   *
   * @param event the Odyssey event to map
   * @return a configured SSE event builder ready to be sent
   */
  SseEmitter.SseEventBuilder map(OdysseyEvent event);

  /** Default mapper that sets id, data, and optional event type. */
  SseEventMapper DEFAULT =
      event -> {
        SseEmitter.SseEventBuilder builder =
            SseEmitter.event().id(event.id()).data(event.payload());
        if (event.eventType() != null) {
          builder.name(event.eventType());
        }
        return builder;
      };
}
