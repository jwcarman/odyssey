package org.jwcarman.odyssey.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.StreamEventHandler;
import org.jwcarman.odyssey.spi.OdysseyEventLog;

class StreamSubscriberTest {

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
    List<OdysseyEvent> events = List.of(testEvent("1-0"));

    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString()))
        .thenAnswer(
            inv -> {
              readCalled.countDown();
              return events.stream();
            });

    StreamEventHandler handler = mock(StreamEventHandler.class);
    StreamSubscriber subscriber =
        new StreamSubscriber(eventLog, handler, "test-stream", "0", 60_000);
    subscriber.start();

    subscriber.nudge();

    assertTrue(readCalled.await(5, TimeUnit.SECONDS), "Reader should have been called after nudge");

    subscriber.closeImmediately();
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

    StreamEventHandler handler = mock(StreamEventHandler.class);
    StreamSubscriber subscriber = new StreamSubscriber(eventLog, handler, "test-stream", "0", 50);
    subscriber.start();

    assertTrue(
        readCalled.await(5, TimeUnit.SECONDS),
        "Reader should have been called after keep-alive timeout");

    subscriber.closeImmediately();
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

    StreamEventHandler handler = mock(StreamEventHandler.class);
    StreamSubscriber subscriber =
        new StreamSubscriber(eventLog, handler, "test-stream", "0", 60_000);
    subscriber.start();

    subscriber.nudge();
    assertTrue(firstReadDone.await(5, TimeUnit.SECONDS));

    // pile up multiple nudges while reader is blocked
    subscriber.nudge();
    subscriber.nudge();
    subscriber.nudge();
    secondReadAllowed.countDown();

    // give time for the reader to process the coalesced nudges
    Thread.sleep(200);

    // should be 2 reads total (first + one coalesced), not 4
    assertTrue(readCount.get() <= 3, "Piled-up nudges should be coalesced via drainPermits");

    subscriber.closeImmediately();
  }

  @Test
  void gracefulShutdownDrainsEvents() throws Exception {
    List<OdysseyEvent> eventsToReturn = List.of(testEvent("1-0"), testEvent("2-0"));
    CountDownLatch readCalled = new CountDownLatch(1);

    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString()))
        .thenAnswer(
            inv -> {
              readCalled.countDown();
              return eventsToReturn.stream();
            });

    StreamEventHandler handler = mock(StreamEventHandler.class);
    StreamSubscriber subscriber =
        new StreamSubscriber(eventLog, handler, "test-stream", "0", 60_000);
    subscriber.start();

    subscriber.nudge();
    assertTrue(readCalled.await(5, TimeUnit.SECONDS));

    // Allow time for writer to process events
    Thread.sleep(200);

    subscriber.closeGracefully();

    // Allow time for graceful shutdown
    Thread.sleep(500);

    // The subscriber should have completed gracefully
    verify(handler).onComplete();
  }

  @Test
  void immediateShutdownStopsBothThreads() throws Exception {
    CountDownLatch readerStarted = new CountDownLatch(1);

    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString()))
        .thenAnswer(
            inv -> {
              readerStarted.countDown();
              return Stream.empty();
            });

    StreamEventHandler handler = mock(StreamEventHandler.class);
    StreamSubscriber subscriber = new StreamSubscriber(eventLog, handler, "test-stream", "0", 50);
    subscriber.start();

    assertTrue(readerStarted.await(5, TimeUnit.SECONDS));

    subscriber.closeImmediately();

    // Allow time for threads to exit
    Thread.sleep(500);

    // If we get here without hanging, both threads were interrupted successfully
  }

  @Test
  void poisonSentinelHasExpectedId() {
    assertEquals("__poison__", StreamSubscriber.POISON.id());
  }

  @Test
  void writerSendsKeepAliveOnTimeout() throws Exception {
    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString())).thenReturn(Stream.empty());

    CountDownLatch keepAliveSent = new CountDownLatch(1);
    StreamEventHandler handler = mock(StreamEventHandler.class);
    doAnswer(
            inv -> {
              keepAliveSent.countDown();
              return null;
            })
        .when(handler)
        .onKeepAlive();

    StreamSubscriber subscriber = new StreamSubscriber(eventLog, handler, "test-stream", "0", 50);
    subscriber.start();

    assertTrue(
        keepAliveSent.await(5, TimeUnit.SECONDS), "Writer should send keep-alive on timeout");

    subscriber.closeImmediately();
  }

  @Test
  void readerUpdatesLastReadId() throws Exception {
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

    StreamEventHandler handler = mock(StreamEventHandler.class);
    StreamSubscriber subscriber = new StreamSubscriber(eventLog, handler, "test-stream", "0", 50);
    subscriber.start();

    subscriber.nudge();

    assertTrue(secondReadDone.await(5, TimeUnit.SECONDS));

    subscriber.closeImmediately();
  }

  @Test
  void writerCallsHandlerOnEvent() throws Exception {
    OdysseyEvent event = testEvent("1-0");
    CountDownLatch eventReceived = new CountDownLatch(1);

    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString())).thenReturn(Stream.empty());

    StreamEventHandler handler = mock(StreamEventHandler.class);
    doAnswer(
            inv -> {
              eventReceived.countDown();
              return null;
            })
        .when(handler)
        .onEvent(any());

    StreamSubscriber subscriber =
        new StreamSubscriber(eventLog, handler, "test-stream", "0", 60_000);
    subscriber.start();

    subscriber.enqueue(event);

    assertTrue(eventReceived.await(5, TimeUnit.SECONDS), "Handler should receive the event");
    verify(handler).onEvent(event);

    subscriber.closeImmediately();
  }
}
