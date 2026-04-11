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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeliveredEventTest {

  @Test
  void nullMetadataBecomesEmptyMap() {
    DeliveredEvent<String> event =
        new DeliveredEvent<>("id", "key", Instant.now(), "type", "data", null);

    assertThat(event.metadata()).isEmpty();
  }

  @Test
  void metadataIsDefensivelyCopied() {
    Map<String, String> original = new HashMap<>();
    original.put("k", "v");

    DeliveredEvent<String> event =
        new DeliveredEvent<>("id", "key", Instant.now(), "type", "data", original);

    original.put("k2", "v2");
    assertThat(event.metadata()).hasSize(1);
  }

  @Test
  void metadataIsUnmodifiable() {
    DeliveredEvent<String> event =
        new DeliveredEvent<>("id", "key", Instant.now(), "type", "data", Map.of("k", "v"));

    assertThatThrownBy(() -> event.metadata().put("k2", "v2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullableEventType() {
    DeliveredEvent<String> event =
        new DeliveredEvent<>("id", "key", Instant.now(), null, "data", Map.of());

    assertThat(event.eventType()).isNull();
  }

  @Test
  void typedDataAccessible() {
    record Payload(int count) {}

    DeliveredEvent<Payload> event =
        new DeliveredEvent<>("id", "key", Instant.now(), "test", new Payload(42), Map.of());

    assertThat(event.data().count()).isEqualTo(42);
  }
}
