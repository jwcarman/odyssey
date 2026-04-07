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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.OdysseyEventLog;

class StreamReaderTest {

  private static OdysseyEvent testEvent(String id) {
    return OdysseyEvent.builder()
        .id(id)
        .streamKey("test-stream")
        .eventType("test")
        .payload("{\"n\":" + id + "}")
        .timestamp(Instant.now())
        .build();
  }

  @Test
  void nudgeWakesReader() throws Exception {
    CountDownLatch readCalled = new CountDownLatch(1);
    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString()))
        .thenAnswer(
            inv -> {
              readCalled.countDown();
              return Stream.of(testEvent("1-0"));
            });

    Semaphore nudge = new Semaphore(0);
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0");
    Thread thread = Thread.ofVirtual().start(reader);

    nudge.release();

    assertTrue(readCalled.await(5, TimeUnit.SECONDS), "Reader should wake on nudge");

    thread.interrupt();
  }

  @Test
  void readerBlocksUntilNudged() {
    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString())).thenReturn(Stream.empty());

    Semaphore nudge = new Semaphore(0);
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0");
    Thread thread = Thread.ofVirtual().start(reader);

    await()
        .during(200, MILLISECONDS)
        .atMost(1, SECONDS)
        .untilAsserted(() -> verify(eventLog, never()).readAfter(anyString(), anyString()));

    nudge.release();
    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> verify(eventLog, atLeastOnce()).readAfter("test-stream", "0"));

    thread.interrupt();
  }

  @Test
  void eventsAreOfferedToQueue() throws Exception {
    OdysseyEvent event1 = testEvent("1-0");
    OdysseyEvent event2 = testEvent("2-0");
    CountDownLatch readCalled = new CountDownLatch(1);

    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString()))
        .thenAnswer(
            inv -> {
              readCalled.countDown();
              return Stream.of(event1, event2);
            });

    Semaphore nudge = new Semaphore(0);
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0");
    Thread thread = Thread.ofVirtual().start(reader);

    nudge.release();
    assertTrue(readCalled.await(5, TimeUnit.SECONDS));
    await().atMost(5, SECONDS).until(() -> queue.size() >= 2);

    assertEquals(event1, queue.poll());
    assertEquals(event2, queue.poll());

    thread.interrupt();
  }

  @Test
  void lastReadIdIsUpdated() throws Exception {
    AtomicInteger callCount = new AtomicInteger(0);
    CountDownLatch secondReadDone = new CountDownLatch(1);

    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString()))
        .thenAnswer(
            inv -> {
              String lastId = inv.getArgument(1);
              int count = callCount.incrementAndGet();
              if (count == 1) {
                assertEquals("0", lastId, "First read should use initial lastReadId");
                return Stream.of(testEvent("5-0"));
              }
              assertEquals("5-0", lastId, "Second read should use updated lastReadId");
              secondReadDone.countDown();
              return Stream.empty();
            });

    Semaphore nudge = new Semaphore(0);
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0");
    Thread thread = Thread.ofVirtual().start(reader);

    nudge.release();
    await().atMost(5, SECONDS).until(() -> callCount.get() >= 1);
    nudge.release();
    assertTrue(secondReadDone.await(5, TimeUnit.SECONDS));

    thread.interrupt();
  }

  @Test
  void drainPermitsCoalescesPiledUpNudges() throws Exception {
    AtomicInteger readCount = new AtomicInteger(0);
    CountDownLatch firstReadDone = new CountDownLatch(1);
    CountDownLatch secondReadAllowed = new CountDownLatch(1);

    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString()))
        .thenAnswer(
            inv -> {
              int count = readCount.incrementAndGet();
              if (count == 1) {
                firstReadDone.countDown();
                secondReadAllowed.await(5, TimeUnit.SECONDS);
              }
              return Stream.empty();
            });

    Semaphore nudge = new Semaphore(0);
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0");
    Thread thread = Thread.ofVirtual().start(reader);

    nudge.release();
    assertTrue(firstReadDone.await(5, TimeUnit.SECONDS));

    // pile up multiple nudges while reader is blocked
    nudge.release();
    nudge.release();
    nudge.release();
    secondReadAllowed.countDown();

    await().atMost(5, SECONDS).until(() -> readCount.get() >= 2);

    assertTrue(readCount.get() <= 3, "Piled-up nudges should be coalesced via drainPermits");

    thread.interrupt();
  }

  @Test
  void interruptExitsCleanly() throws Exception {
    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString())).thenReturn(Stream.empty());

    Semaphore nudge = new Semaphore(0);
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0");
    Thread thread = Thread.ofVirtual().start(reader);

    await()
        .atMost(5, SECONDS)
        .until(
            () ->
                thread.getState() == Thread.State.WAITING
                    || thread.getState() == Thread.State.TIMED_WAITING);
    thread.interrupt();
    thread.join(2000);

    assertFalse(thread.isAlive(), "Reader thread should exit on interrupt");
  }
}
