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
package org.jwcarman.odyssey.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;

class InMemoryOdysseyEventLogTest {

  private static final String STREAM_KEY = "odyssey:channel:test";

  private InMemoryOdysseyEventLog eventLog;

  @BeforeEach
  void setUp() {
    eventLog = new InMemoryOdysseyEventLog(100);
  }

  @Test
  void appendReturnsMonotonicallyIncreasingIds() {
    String id1 = eventLog.append(STREAM_KEY, testEvent("evt1", "p1"));
    String id2 = eventLog.append(STREAM_KEY, testEvent("evt2", "p2"));
    String id3 = eventLog.append(STREAM_KEY, testEvent("evt3", "p3"));

    assertTrue(id1.compareTo(id2) < 0 || parseId(id1) < parseId(id2));
    assertTrue(id2.compareTo(id3) < 0 || parseId(id2) < parseId(id3));
  }

  @Test
  void appendAndReadAfterReturnsEventsAfterCursor() {
    String id1 = eventLog.append(STREAM_KEY, testEvent("evt1", "p1"));
    String id2 = eventLog.append(STREAM_KEY, testEvent("evt2", "p2"));
    String id3 = eventLog.append(STREAM_KEY, testEvent("evt3", "p3"));

    List<OdysseyEvent> events = eventLog.readAfter(STREAM_KEY, id1).toList();

    assertEquals(2, events.size());
    assertEquals(id2, events.get(0).id());
    assertEquals(id3, events.get(1).id());
  }

  @Test
  void readAfterWithZeroCursorReturnsAllEvents() {
    eventLog.append(STREAM_KEY, testEvent("evt1", "p1"));
    eventLog.append(STREAM_KEY, testEvent("evt2", "p2"));

    List<OdysseyEvent> events = eventLog.readAfter(STREAM_KEY, "0-0").toList();

    assertEquals(2, events.size());
  }

  @Test
  void readAfterOnNonExistentStreamReturnsEmpty() {
    List<OdysseyEvent> events = eventLog.readAfter("nonexistent", "0-0").toList();

    assertTrue(events.isEmpty());
  }

  @Test
  void readLastReturnsLastNEventsInOrder() {
    eventLog.append(STREAM_KEY, testEvent("evt1", "p1"));
    eventLog.append(STREAM_KEY, testEvent("evt2", "p2"));
    eventLog.append(STREAM_KEY, testEvent("evt3", "p3"));
    eventLog.append(STREAM_KEY, testEvent("evt4", "p4"));

    List<OdysseyEvent> events = eventLog.readLast(STREAM_KEY, 2).toList();

    assertEquals(2, events.size());
    assertEquals("evt3", events.get(0).eventType());
    assertEquals("evt4", events.get(1).eventType());
  }

  @Test
  void readLastWithCountExceedingSizeReturnsAll() {
    eventLog.append(STREAM_KEY, testEvent("evt1", "p1"));
    eventLog.append(STREAM_KEY, testEvent("evt2", "p2"));

    List<OdysseyEvent> events = eventLog.readLast(STREAM_KEY, 10).toList();

    assertEquals(2, events.size());
  }

  @Test
  void readLastOnNonExistentStreamReturnsEmpty() {
    List<OdysseyEvent> events = eventLog.readLast("nonexistent", 5).toList();

    assertTrue(events.isEmpty());
  }

  @Test
  void evictionRemovesOldestEventsWhenFull() {
    InMemoryOdysseyEventLog smallLog = new InMemoryOdysseyEventLog(3);

    smallLog.append(STREAM_KEY, testEvent("evt1", "p1"));
    smallLog.append(STREAM_KEY, testEvent("evt2", "p2"));
    smallLog.append(STREAM_KEY, testEvent("evt3", "p3"));
    smallLog.append(STREAM_KEY, testEvent("evt4", "p4"));
    smallLog.append(STREAM_KEY, testEvent("evt5", "p5"));

    List<OdysseyEvent> events = smallLog.readAfter(STREAM_KEY, "0-0").toList();

    assertEquals(3, events.size());
    assertEquals("evt3", events.get(0).eventType());
    assertEquals("evt4", events.get(1).eventType());
    assertEquals("evt5", events.get(2).eventType());
  }

  @Test
  void deleteRemovesStream() {
    eventLog.append(STREAM_KEY, testEvent("evt1", "p1"));

    eventLog.delete(STREAM_KEY);

    List<OdysseyEvent> events = eventLog.readAfter(STREAM_KEY, "0-0").toList();
    assertTrue(events.isEmpty());
  }

  @Test
  void appendPreservesEventFields() {
    OdysseyEvent input = testEvent("myType", "myPayload");
    String id = eventLog.append(STREAM_KEY, input);

    List<OdysseyEvent> events = eventLog.readAfter(STREAM_KEY, "0-0").toList();

    assertEquals(1, events.size());
    OdysseyEvent stored = events.getFirst();
    assertEquals(id, stored.id());
    assertEquals(STREAM_KEY, stored.streamKey());
    assertEquals("myType", stored.eventType());
    assertEquals("myPayload", stored.payload());
    assertNotNull(stored.timestamp());
  }

  @Test
  void readAfterHandlesIdWithoutDash() {
    eventLog.append(STREAM_KEY, testEvent("evt1", "p1"));

    List<OdysseyEvent> events = eventLog.readAfter(STREAM_KEY, "0").toList();

    assertEquals(1, events.size());
  }

  @Test
  void separateStreamKeysAreIndependent() {
    String key1 = "odyssey:channel:one";
    String key2 = "odyssey:channel:two";

    eventLog.append(key1, testEvent("evt1", "p1"));
    eventLog.append(key2, testEvent("evt2", "p2"));

    assertEquals(1, eventLog.readAfter(key1, "0-0").toList().size());
    assertEquals(1, eventLog.readAfter(key2, "0-0").toList().size());

    eventLog.delete(key1);

    assertTrue(eventLog.readAfter(key1, "0-0").toList().isEmpty());
    assertEquals(1, eventLog.readAfter(key2, "0-0").toList().size());
  }

  private static OdysseyEvent testEvent(String eventType, String payload) {
    return OdysseyEvent.builder()
        .streamKey(STREAM_KEY)
        .eventType(eventType)
        .payload(payload)
        .timestamp(Instant.now())
        .metadata(Map.of())
        .build();
  }

  private static long parseId(String id) {
    int dash = id.indexOf('-');
    return Long.parseLong(dash >= 0 ? id.substring(0, dash) : id);
  }
}
