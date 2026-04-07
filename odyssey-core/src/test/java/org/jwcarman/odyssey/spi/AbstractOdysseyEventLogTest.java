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
package org.jwcarman.odyssey.spi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;

class AbstractOdysseyEventLogTest {

  private static final String EPHEMERAL_PREFIX = "test:ephemeral:";
  private static final String CHANNEL_PREFIX = "test:channel:";
  private static final String BROADCAST_PREFIX = "test:broadcast:";

  private final TestEventLog eventLog =
      new TestEventLog(EPHEMERAL_PREFIX, CHANNEL_PREFIX, BROADCAST_PREFIX);

  @Test
  void ephemeralKeyStartsWithEphemeralPrefix() {
    String key = eventLog.ephemeralKey();
    assertTrue(key.startsWith(EPHEMERAL_PREFIX));
  }

  @Test
  void ephemeralKeyReturnsUniqueValues() {
    String key1 = eventLog.ephemeralKey();
    String key2 = eventLog.ephemeralKey();
    assertNotEquals(key1, key2);
  }

  @Test
  void channelKeyPrependsChannelPrefix() {
    String key = eventLog.channelKey("myChannel");
    assertEquals(CHANNEL_PREFIX + "myChannel", key);
  }

  @Test
  void broadcastKeyPrependsBroadcastPrefix() {
    String key = eventLog.broadcastKey("myBroadcast");
    assertEquals(BROADCAST_PREFIX + "myBroadcast", key);
  }

  @Test
  void generateEventIdReturnsNonNull() {
    String eventId = eventLog.exposedGenerateEventId();
    assertNotNull(eventId);
  }

  @Test
  void generateEventIdReturnsUniqueValues() {
    String id1 = eventLog.exposedGenerateEventId();
    String id2 = eventLog.exposedGenerateEventId();
    assertNotEquals(id1, id2);
  }

  @Test
  void ephemeralPrefixReturnsCorrectValue() {
    assertEquals(EPHEMERAL_PREFIX, eventLog.exposedEphemeralPrefix());
  }

  @Test
  void channelPrefixReturnsCorrectValue() {
    assertEquals(CHANNEL_PREFIX, eventLog.exposedChannelPrefix());
  }

  @Test
  void broadcastPrefixReturnsCorrectValue() {
    assertEquals(BROADCAST_PREFIX, eventLog.exposedBroadcastPrefix());
  }

  private static class TestEventLog extends AbstractOdysseyEventLog {

    TestEventLog(String ephemeralPrefix, String channelPrefix, String broadcastPrefix) {
      super(ephemeralPrefix, channelPrefix, broadcastPrefix);
    }

    String exposedGenerateEventId() {
      return generateEventId();
    }

    String exposedEphemeralPrefix() {
      return ephemeralPrefix();
    }

    String exposedChannelPrefix() {
      return channelPrefix();
    }

    String exposedBroadcastPrefix() {
      return broadcastPrefix();
    }

    @Override
    public String append(String streamKey, OdysseyEvent event) {
      return null;
    }

    @Override
    public Stream<OdysseyEvent> readAfter(String streamKey, String lastId) {
      return Stream.empty();
    }

    @Override
    public Stream<OdysseyEvent> readLast(String streamKey, int count) {
      return Stream.empty();
    }

    @Override
    public void delete(String streamKey) {
      // no-op
    }
  }
}
