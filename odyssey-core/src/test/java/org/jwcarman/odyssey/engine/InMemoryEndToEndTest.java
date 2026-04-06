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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.memory.InMemoryOdysseyStreamNotifier;

class InMemoryEndToEndTest {

  private static final long KEEP_ALIVE_INTERVAL = 500;
  private static final long SSE_TIMEOUT = 0;
  private static final int MAX_LAST_N = 500;

  private InMemoryOdysseyEventLog eventLog;
  private InMemoryOdysseyStreamNotifier notifier;
  private DefaultOdysseyStreamRegistry registry;

  @BeforeEach
  void setUp() {
    eventLog = new InMemoryOdysseyEventLog(100);
    notifier = new InMemoryOdysseyStreamNotifier();
    registry =
        new DefaultOdysseyStreamRegistry(
            eventLog, notifier, KEEP_ALIVE_INTERVAL, SSE_TIMEOUT, MAX_LAST_N);
  }

  @AfterEach
  void tearDown() {
    registry.channel("test").close();
  }

  @Test
  void publishStoresEventsInEventLog() {
    OdysseyStream stream = registry.channel("test");
    String streamKey = stream.getStreamKey();

    stream.publish("greeting", "hello");
    stream.publish("greeting", "world");

    List<OdysseyEvent> events = eventLog.readAfter(streamKey, "0-0").toList();
    assertEquals(2, events.size());
    assertEquals("hello", events.get(0).payload());
    assertEquals("world", events.get(1).payload());
  }

  @Test
  void publishTriggersNotificationToRegisteredHandlers() {
    List<String> notifications = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> notifications.add(streamKey));

    OdysseyStream stream = registry.channel("test");
    String streamKey = stream.getStreamKey();
    stream.publish("msg", "data");

    assertTrue(notifications.stream().anyMatch(k -> k.equals(streamKey)));
  }

  @Test
  void resumeAfterReplaysStoredEvents() {
    OdysseyStream stream = registry.channel("test");
    String streamKey = stream.getStreamKey();
    String id1 = stream.publish("msg", "first");
    stream.publish("msg", "second");
    stream.publish("msg", "third");

    List<OdysseyEvent> events = eventLog.readAfter(streamKey, id1).toList();

    assertEquals(2, events.size());
    assertEquals("second", events.get(0).payload());
    assertEquals("third", events.get(1).payload());
  }

  @Test
  void replayLastReturnsLastNEvents() {
    OdysseyStream stream = registry.channel("test");
    String streamKey = stream.getStreamKey();
    stream.publish("msg", "a");
    stream.publish("msg", "b");
    stream.publish("msg", "c");
    stream.publish("msg", "d");

    List<OdysseyEvent> events = eventLog.readLast(streamKey, 2).toList();

    assertEquals(2, events.size());
    assertEquals("c", events.get(0).payload());
    assertEquals("d", events.get(1).payload());
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
    assertTrue(s1.getStreamKey().startsWith("ephemeral:"));
    assertTrue(s2.getStreamKey().startsWith("ephemeral:"));

    s1.close();
    s2.close();
  }

  @Test
  void channelStreamHasConsistentKey() {
    OdysseyStream s1 = registry.channel("same");
    OdysseyStream s2 = registry.channel("same");

    assertEquals(s1.getStreamKey(), s2.getStreamKey());
    assertEquals("channel:same", s1.getStreamKey());

    s1.close();
  }

  @Test
  void broadcastStreamHasConsistentKey() {
    OdysseyStream s1 = registry.broadcast("news");
    OdysseyStream s2 = registry.broadcast("news");

    assertEquals(s1.getStreamKey(), s2.getStreamKey());
    assertEquals("broadcast:news", s1.getStreamKey());

    s1.close();
  }

  @Test
  void deleteRemovesEventsFromLog() {
    OdysseyStream stream = registry.channel("test");
    String streamKey = stream.getStreamKey();
    stream.publish("msg", "hello");

    stream.delete();

    List<OdysseyEvent> events = eventLog.readAfter(streamKey, "0-0").toList();
    assertTrue(events.isEmpty());
  }

  @Test
  void notificationFlowConnectsPublishToSubscriberNudge() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    notifier.subscribe((streamKey, eventId) -> latch.countDown());

    OdysseyStream stream = registry.channel("test");
    stream.publish("msg", "trigger");

    assertTrue(latch.await(2, TimeUnit.SECONDS));
  }
}
