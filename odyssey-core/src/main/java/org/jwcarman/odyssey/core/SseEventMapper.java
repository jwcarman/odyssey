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

public interface SseEventMapper<T> {

  SseEmitter.SseEventBuilder map(DeliveredEvent<T> event);

  default Optional<SseEmitter.SseEventBuilder> terminal(TerminalReason reason) {
    return Optional.of(SseEmitter.event().name("odyssey-" + reason.name().toLowerCase()).data(""));
  }

  enum TerminalReason {
    COMPLETED,
    EXPIRED,
    DELETED,
    ERRORED
  }

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
