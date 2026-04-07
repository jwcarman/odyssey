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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.substrate.core.Journal;
import org.jwcarman.substrate.core.JournalEntry;
import org.jwcarman.substrate.core.JournalFactory;
import org.jwcarman.substrate.memory.InMemoryJournalSpi;
import org.jwcarman.substrate.memory.InMemoryNotifier;
import tools.jackson.databind.ObjectMapper;

class InMemoryEndToEndTest {

  private static final long KEEP_ALIVE_INTERVAL = 500;
  private static final long SSE_TIMEOUT = 0;

  private InMemoryJournalSpi journalSpi;
  private InMemoryNotifier notifier;
  private JournalFactory journalFactory;
  private DefaultOdysseyStreamRegistry registry;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();
    journalSpi = new InMemoryJournalSpi(100);
    notifier = new InMemoryNotifier();
    journalFactory =
        new JournalFactory(journalSpi, new JacksonCodecFactory(objectMapper), notifier);
    registry =
        new DefaultOdysseyStreamRegistry(
            journalFactory, objectMapper, KEEP_ALIVE_INTERVAL, SSE_TIMEOUT);
  }

  @AfterEach
  void tearDown() {
    registry.channel("test").close();
  }

  @Test
  void publishStoresEventsInJournal() {
    OdysseyStream stream = registry.channel("test");
    String id1 = stream.publishRaw("greeting", "hello");
    String id2 = stream.publishRaw("greeting", "world");

    assertNotNull(id1);
    assertNotNull(id2);
    assertNotEquals(id1, id2);
  }

  @Test
  void subscribeReturnsValidEmitter() {
    OdysseyStream stream = registry.channel("test");

    var emitter = stream.subscribe();

    assertNotNull(emitter);
  }

  @Test
  void ephemeralStreamHasUniqueKey() {
    OdysseyStream s1 = registry.ephemeral();
    OdysseyStream s2 = registry.ephemeral();

    assertNotEquals(s1.getStreamKey(), s2.getStreamKey());
    assertTrue(s1.getStreamKey().contains("ephemeral:"));
    assertTrue(s2.getStreamKey().contains("ephemeral:"));

    s1.close();
    s2.close();
  }

  @Test
  void channelStreamHasConsistentKey() {
    OdysseyStream s1 = registry.channel("same");
    OdysseyStream s2 = registry.channel("same");

    assertEquals(s1.getStreamKey(), s2.getStreamKey());
    assertTrue(s1.getStreamKey().contains("channel:same"));

    s1.close();
  }

  @Test
  void broadcastStreamHasConsistentKey() {
    OdysseyStream s1 = registry.broadcast("news");
    OdysseyStream s2 = registry.broadcast("news");

    assertEquals(s1.getStreamKey(), s2.getStreamKey());
    assertTrue(s1.getStreamKey().contains("broadcast:news"));

    s1.close();
  }

  @Test
  void publishAndReadThroughJournal() {
    OdysseyStream stream = registry.channel("test");
    String id1 = stream.publishRaw("msg", "hello");
    stream.publishRaw("msg", "world");

    Journal<OdysseyEvent> journal = journalFactory.create("channel:test", OdysseyEvent.class);
    var cursor = journal.readAfter(id1);
    Optional<JournalEntry<OdysseyEvent>> entry = cursor.poll(Duration.ofSeconds(2));
    assertTrue(entry.isPresent());
    assertEquals("world", entry.get().data().payload());
    cursor.close();
  }

  @Test
  void replayLastReadsThroughJournal() {
    OdysseyStream stream = registry.channel("test");
    stream.publishRaw("msg", "a");
    stream.publishRaw("msg", "b");
    stream.publishRaw("msg", "c");
    stream.publishRaw("msg", "d");

    Journal<OdysseyEvent> journal = journalFactory.create("channel:test", OdysseyEvent.class);
    var cursor = journal.readLast(2);
    List<String> payloads = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      Optional<JournalEntry<OdysseyEvent>> entry = cursor.poll(Duration.ofSeconds(2));
      entry.ifPresent(e -> payloads.add(e.data().payload()));
    }
    cursor.close();

    assertEquals(2, payloads.size());
    assertEquals("c", payloads.get(0));
    assertEquals("d", payloads.get(1));
  }

  @Test
  void deleteRemovesJournal() {
    OdysseyStream stream = registry.channel("test");
    stream.publishRaw("msg", "hello");

    stream.delete();

    Journal<OdysseyEvent> journal = journalFactory.create("channel:test", OdysseyEvent.class);
    var cursor = journal.readLast(10);
    Optional<JournalEntry<OdysseyEvent>> entry = cursor.poll(Duration.ofMillis(200));
    assertFalse(entry.isPresent());
    cursor.close();
  }
}
