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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoredEventTest {

  @Test
  void nullMetadataBecomesEmptyMap() {
    StoredEvent event = new StoredEvent("type", "data", null);

    assertThat(event.metadata()).isEmpty();
  }

  @Test
  void metadataIsDefensivelyCopied() {
    Map<String, String> original = new HashMap<>();
    original.put("k", "v");

    StoredEvent event = new StoredEvent("type", "data", original);

    original.put("k2", "v2");
    assertThat(event.metadata()).hasSize(1);
  }

  @Test
  void metadataIsUnmodifiable() {
    StoredEvent event = new StoredEvent("type", "data", Map.of("k", "v"));
    Map<String, String> metadata = event.metadata();

    assertThatThrownBy(() -> metadata.put("k2", "v2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullableEventType() {
    StoredEvent event = new StoredEvent(null, "data", Map.of());

    assertThat(event.eventType()).isNull();
  }
}
