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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OdysseyEventTest {

  @Test
  void builderConstructsEventWithAllFields() {
    Instant now = Instant.now();
    Map<String, String> metadata = Map.of("key", "value");

    OdysseyEvent event =
        OdysseyEvent.builder()
            .id("1-0")
            .streamKey("odyssey:channel:test")
            .eventType("message")
            .payload("{\"text\":\"hello\"}")
            .timestamp(now)
            .metadata(metadata)
            .build();

    assertEquals("1-0", event.id());
    assertEquals("odyssey:channel:test", event.streamKey());
    assertEquals("message", event.eventType());
    assertEquals("{\"text\":\"hello\"}", event.payload());
    assertEquals(now, event.timestamp());
    assertEquals(Map.of("key", "value"), event.metadata());
  }

  @Test
  void metadataDefaultsToEmptyMap() {
    OdysseyEvent event = OdysseyEvent.builder().build();

    assertNotNull(event.metadata());
    assertTrue(event.metadata().isEmpty());
  }

  @Test
  void metadataIsDefensivelyCopied() {
    Map<String, String> mutable = new HashMap<>();
    mutable.put("key", "original");

    OdysseyEvent event = OdysseyEvent.builder().metadata(mutable).build();

    mutable.put("key", "mutated");
    mutable.put("extra", "added");

    assertEquals("original", event.metadata().get("key"));
    assertNull(event.metadata().get("extra"));
  }

  @Test
  void metadataIsUnmodifiable() {
    OdysseyEvent event = OdysseyEvent.builder().metadata(Map.of("key", "value")).build();

    Map<String, String> metadata = event.metadata();
    assertThrows(UnsupportedOperationException.class, () -> metadata.put("new", "entry"));
  }

  @Test
  void nullMetadataBecomesEmptyMap() {
    OdysseyEvent event = new OdysseyEvent("1-0", "key", "type", "payload", Instant.now(), null);

    assertNotNull(event.metadata());
    assertTrue(event.metadata().isEmpty());
  }
}
