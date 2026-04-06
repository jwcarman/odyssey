package org.jwcarman.odyssey.engine;

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
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0", 60_000);
    Thread thread = Thread.ofVirtual().start(reader);

    nudge.release();

    assertTrue(readCalled.await(5, TimeUnit.SECONDS), "Reader should wake on nudge");

    thread.interrupt();
  }

  @Test
  void timeoutWakesReader() throws Exception {
    CountDownLatch readCalled = new CountDownLatch(1);
    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString()))
        .thenAnswer(
            inv -> {
              readCalled.countDown();
              return Stream.empty();
            });

    Semaphore nudge = new Semaphore(0);
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0", 50);
    Thread thread = Thread.ofVirtual().start(reader);

    assertTrue(readCalled.await(5, TimeUnit.SECONDS), "Reader should wake on keep-alive timeout");

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
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0", 60_000);
    Thread thread = Thread.ofVirtual().start(reader);

    nudge.release();
    assertTrue(readCalled.await(5, TimeUnit.SECONDS));
    Thread.sleep(100);

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
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0", 50);
    Thread thread = Thread.ofVirtual().start(reader);

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
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0", 60_000);
    Thread thread = Thread.ofVirtual().start(reader);

    nudge.release();
    assertTrue(firstReadDone.await(5, TimeUnit.SECONDS));

    // pile up multiple nudges while reader is blocked
    nudge.release();
    nudge.release();
    nudge.release();
    secondReadAllowed.countDown();

    Thread.sleep(200);

    assertTrue(readCount.get() <= 3, "Piled-up nudges should be coalesced via drainPermits");

    thread.interrupt();
  }

  @Test
  void interruptExitsCleanly() throws Exception {
    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString())).thenReturn(Stream.empty());

    Semaphore nudge = new Semaphore(0);
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    StreamReader reader = new StreamReader(eventLog, "test-stream", nudge, queue, "0", 60_000);
    Thread thread = Thread.ofVirtual().start(reader);

    Thread.sleep(50);
    thread.interrupt();
    thread.join(2000);

    assertFalse(thread.isAlive(), "Reader thread should exit on interrupt");
  }
}
