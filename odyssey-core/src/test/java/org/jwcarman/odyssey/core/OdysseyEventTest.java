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

    assertThrows(UnsupportedOperationException.class, () -> event.metadata().put("new", "entry"));
  }

  @Test
  void nullMetadataBecomesEmptyMap() {
    OdysseyEvent event = new OdysseyEvent("1-0", "key", "type", "payload", Instant.now(), null);

    assertNotNull(event.metadata());
    assertTrue(event.metadata().isEmpty());
  }
}
