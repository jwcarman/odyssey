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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DefaultOdysseyStreamTest {

  private static final String STREAM_KEY = "odyssey:channel:test";
  private static final long KEEP_ALIVE = 30_000;
  private static final long SSE_TIMEOUT = 0;
  private static final int MAX_LAST_N = 500;

  @Mock private OdysseyEventLog eventLog;
  @Mock private OdysseyStreamNotifier notifier;

  private StreamSubscriberGroup subscriberGroup;
  private DefaultOdysseyStream stream;

  @BeforeEach
  void setUp() {
    subscriberGroup = spy(new StreamSubscriberGroup());
    stream =
        new DefaultOdysseyStream(
            STREAM_KEY,
            eventLog,
            notifier,
            subscriberGroup,
            new DefaultOdysseyStream.StreamConfig(KEEP_ALIVE, SSE_TIMEOUT, MAX_LAST_N),
            new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    stream.close();
  }

  @Test
  void publishCallsAppendAndNotify() {
    when(eventLog.append(eq(STREAM_KEY), any(OdysseyEvent.class))).thenReturn("1-0");

    String entryId = stream.publishRaw("test-event", "{\"data\":1}");

    assertEquals("1-0", entryId);
    verify(eventLog).append(eq(STREAM_KEY), any(OdysseyEvent.class));
    verify(notifier).notify(STREAM_KEY, "1-0");
  }

  @Test
  void subscribeReturnsEmitter() {
    lenient().when(eventLog.readLast(STREAM_KEY, 1)).thenReturn(Stream.empty());

    var emitter = stream.subscribe();

    assertNotNull(emitter);
  }

  @Test
  void subscribeWithTimeoutReturnsEmitter() {
    lenient().when(eventLog.readLast(STREAM_KEY, 1)).thenReturn(Stream.empty());

    var emitter = stream.subscribe(Duration.ofSeconds(60));

    assertNotNull(emitter);
  }

  @Test
  void subscribeQueriesCurrentLastId() {
    when(eventLog.readLast(STREAM_KEY, 1)).thenReturn(Stream.empty());

    stream.subscribe();

    verify(eventLog).readLast(STREAM_KEY, 1);
  }

  @Test
  void resumeAfterCallsReadAfter() {
    when(eventLog.readAfter(STREAM_KEY, "5-0")).thenReturn(Stream.empty());

    stream.resumeAfter("5-0");

    verify(eventLog).readAfter(STREAM_KEY, "5-0");
  }

  @Test
  void resumeAfterReturnsEmitter() {
    when(eventLog.readAfter(STREAM_KEY, "5-0")).thenReturn(Stream.empty());

    var emitter = stream.resumeAfter("5-0");

    assertNotNull(emitter);
  }

  @Test
  void resumeAfterWithTimeoutReturnsEmitter() {
    when(eventLog.readAfter(STREAM_KEY, "5-0")).thenReturn(Stream.empty());

    var emitter = stream.resumeAfter("5-0", Duration.ofSeconds(60));

    assertNotNull(emitter);
  }

  @Test
  void resumeAfterReplaysEventsFromReadAfter() {
    OdysseyEvent evt1 = testEvent("6-0", "evt1", "p1");
    OdysseyEvent evt2 = testEvent("7-0", "evt2", "p2");
    when(eventLog.readAfter(STREAM_KEY, "5-0")).thenReturn(Stream.of(evt1, evt2));

    var emitter = stream.resumeAfter("5-0");

    assertNotNull(emitter);
    verify(eventLog).readAfter(STREAM_KEY, "5-0");
  }

  @Test
  void replayLastCallsReadLast() {
    when(eventLog.readLast(STREAM_KEY, 10)).thenReturn(Stream.empty());
    when(eventLog.readLast(STREAM_KEY, 1)).thenReturn(Stream.empty());

    stream.replayLast(10);

    verify(eventLog).readLast(STREAM_KEY, 10);
  }

  @Test
  void replayLastCapsCountAtMaxLastN() {
    when(eventLog.readLast(STREAM_KEY, MAX_LAST_N)).thenReturn(Stream.empty());
    when(eventLog.readLast(STREAM_KEY, 1)).thenReturn(Stream.empty());

    stream.replayLast(1000);

    verify(eventLog).readLast(STREAM_KEY, MAX_LAST_N);
  }

  @Test
  void replayLastReturnsEmitter() {
    when(eventLog.readLast(STREAM_KEY, 5)).thenReturn(Stream.empty());
    when(eventLog.readLast(STREAM_KEY, 1)).thenReturn(Stream.empty());

    var emitter = stream.replayLast(5);

    assertNotNull(emitter);
  }

  @Test
  void replayLastWithTimeoutReturnsEmitter() {
    when(eventLog.readLast(STREAM_KEY, 5)).thenReturn(Stream.empty());
    when(eventLog.readLast(STREAM_KEY, 1)).thenReturn(Stream.empty());

    var emitter = stream.replayLast(5, Duration.ofSeconds(60));

    assertNotNull(emitter);
  }

  @Test
  void closeShutsFanoutGracefully() {
    stream.close();

    verify(subscriberGroup).shutdown();
  }

  @Test
  void deleteShutsFanoutImmediatelyAndDeletesFromEventLog() {
    stream.delete();

    verify(subscriberGroup).shutdownImmediately();
    verify(eventLog).delete(STREAM_KEY);
  }

  @Test
  void getStreamKeyReturnsKey() {
    assertEquals(STREAM_KEY, stream.getStreamKey());
  }

  private static OdysseyEvent testEvent(String id, String eventType, String payload) {
    return OdysseyEvent.builder()
        .id(id)
        .streamKey(STREAM_KEY)
        .eventType(eventType)
        .payload(payload)
        .timestamp(Instant.now())
        .metadata(Map.of())
        .build();
  }
}
