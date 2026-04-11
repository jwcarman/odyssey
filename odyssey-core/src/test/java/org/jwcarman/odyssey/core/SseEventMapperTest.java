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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class SseEventMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  record TestPayload(String msg) {}

  @Test
  void defaultMapperProducesSseEvent() {
    SseEventMapper<TestPayload> mapper = SseEventMapper.defaultMapper(objectMapper);
    DeliveredEvent<TestPayload> event =
        new DeliveredEvent<>(
            "id-1", "stream-key", Instant.now(), "test.type", new TestPayload("hello"), Map.of());

    SseEmitter.SseEventBuilder result = mapper.map(event);

    assertThat(result).isNotNull();
  }

  @Test
  void defaultMapperHandlesNullEventType() {
    SseEventMapper<TestPayload> mapper = SseEventMapper.defaultMapper(objectMapper);
    DeliveredEvent<TestPayload> event =
        new DeliveredEvent<>(
            "id-1", "stream-key", Instant.now(), null, new TestPayload("hello"), Map.of());

    SseEmitter.SseEventBuilder result = mapper.map(event);

    assertThat(result).isNotNull();
  }

  @Test
  void terminalDefaultReturnsEmptyForEveryState() {
    SseEventMapper<TestPayload> mapper = SseEventMapper.defaultMapper(objectMapper);

    assertThat(mapper.terminal(new SseEventMapper.TerminalState.Completed())).isEmpty();
    assertThat(mapper.terminal(new SseEventMapper.TerminalState.Expired())).isEmpty();
    assertThat(mapper.terminal(new SseEventMapper.TerminalState.Deleted())).isEmpty();
    assertThat(
            mapper.terminal(new SseEventMapper.TerminalState.Errored(new RuntimeException("boom"))))
        .isEmpty();
  }

  @Test
  void erroredTerminalStateCarriesCause() {
    RuntimeException cause = new RuntimeException("backend exploded");
    SseEventMapper.TerminalState.Errored errored = new SseEventMapper.TerminalState.Errored(cause);
    assertThat(errored.cause()).isSameAs(cause);
  }

  @Test
  void terminalCanBeOverriddenToEmitAFrame() {
    SseEventMapper<TestPayload> mapper =
        new SseEventMapper<>() {
          @Override
          public SseEmitter.SseEventBuilder map(DeliveredEvent<TestPayload> event) {
            return SseEmitter.event().data("test");
          }

          @Override
          public Optional<SseEmitter.SseEventBuilder> terminal(TerminalState state) {
            return switch (state) {
              case TerminalState.Completed c -> Optional.of(SseEmitter.event().name("done"));
              case TerminalState.Expired e -> Optional.of(SseEmitter.event().name("expired"));
              case TerminalState.Deleted d -> Optional.of(SseEmitter.event().name("deleted"));
              case TerminalState.Errored err ->
                  Optional.of(SseEmitter.event().name("errored").data(err.cause().getMessage()));
            };
          }
        };

    assertThat(mapper.terminal(new SseEventMapper.TerminalState.Completed())).isPresent();
    assertThat(mapper.terminal(new SseEventMapper.TerminalState.Errored(new RuntimeException("x"))))
        .isPresent();
  }
}
