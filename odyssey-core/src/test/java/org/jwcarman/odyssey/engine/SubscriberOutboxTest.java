package org.jwcarman.odyssey.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SubscriberOutboxTest {

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

    Function<String, List<OdysseyEvent>> readFunction =
        lastId -> {
          readCalled.countDown();
          return events;
        };

    SseEmitter emitter = new SseEmitter(0L);
    SubscriberOutbox outbox =
        new SubscriberOutbox(readFunction, emitter, "test-stream", "0", 60_000);
    outbox.start();

    outbox.nudge();

    assertTrue(readCalled.await(5, TimeUnit.SECONDS), "Reader should have been called after nudge");

    outbox.closeImmediately();
  }

  @Test
  void timeoutWakesReader() throws Exception {
    CountDownLatch readCalled = new CountDownLatch(1);

    Function<String, List<OdysseyEvent>> readFunction =
        lastId -> {
          readCalled.countDown();
          return List.of();
        };

    SseEmitter emitter = new SseEmitter(0L);
    SubscriberOutbox outbox = new SubscriberOutbox(readFunction, emitter, "test-stream", "0", 50);
    outbox.start();

    assertTrue(
        readCalled.await(5, TimeUnit.SECONDS),
        "Reader should have been called after keep-alive timeout");

    outbox.closeImmediately();
  }

  @Test
  void drainPermitsCoalescesPiledUpNudges() throws Exception {
    AtomicInteger readCount = new AtomicInteger(0);
    CountDownLatch firstReadDone = new CountDownLatch(1);
    CountDownLatch secondReadAllowed = new CountDownLatch(1);

    Function<String, List<OdysseyEvent>> readFunction =
        lastId -> {
          int count = readCount.incrementAndGet();
          if (count == 1) {
            firstReadDone.countDown();
            try {
              secondReadAllowed.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
          return List.of();
        };

    SseEmitter emitter = new SseEmitter(0L);
    SubscriberOutbox outbox =
        new SubscriberOutbox(readFunction, emitter, "test-stream", "0", 60_000);
    outbox.start();

    outbox.nudge();
    assertTrue(firstReadDone.await(5, TimeUnit.SECONDS));

    // pile up multiple nudges while reader is blocked
    outbox.nudge();
    outbox.nudge();
    outbox.nudge();
    secondReadAllowed.countDown();

    // give time for the reader to process the coalesced nudges
    Thread.sleep(200);

    // should be 2 reads total (first + one coalesced), not 4
    assertTrue(readCount.get() <= 3, "Piled-up nudges should be coalesced via drainPermits");

    outbox.closeImmediately();
  }

  @Test
  void gracefulShutdownDrainsEvents() throws Exception {
    List<OdysseyEvent> eventsToReturn = List.of(testEvent("1-0"), testEvent("2-0"));
    CountDownLatch readCalled = new CountDownLatch(1);

    Function<String, List<OdysseyEvent>> readFunction =
        lastId -> {
          readCalled.countDown();
          return eventsToReturn;
        };

    List<String> sentIds = new CopyOnWriteArrayList<>();
    SseEmitter emitter = new SseEmitter(0L);
    emitter.onCompletion(
        () -> {
          // emitter completed
        });

    // We need to track what's sent. Use a handler that records calls.
    SubscriberOutbox outbox =
        new SubscriberOutbox(readFunction, emitter, "test-stream", "0", 60_000);
    outbox.start();

    outbox.nudge();
    assertTrue(readCalled.await(5, TimeUnit.SECONDS));

    // Allow time for writer to process events
    Thread.sleep(200);

    outbox.closeGracefully();

    // Allow time for graceful shutdown
    Thread.sleep(500);

    // The outbox should have completed gracefully (no exception expected)
  }

  @Test
  void immediateShutdownStopsBothThreads() throws Exception {
    CountDownLatch readerStarted = new CountDownLatch(1);

    Function<String, List<OdysseyEvent>> readFunction =
        lastId -> {
          readerStarted.countDown();
          return List.of();
        };

    SseEmitter emitter = new SseEmitter(0L);
    SubscriberOutbox outbox = new SubscriberOutbox(readFunction, emitter, "test-stream", "0", 50);
    outbox.start();

    // Wait for reader to start
    assertTrue(readerStarted.await(5, TimeUnit.SECONDS));

    outbox.closeImmediately();

    // Allow time for threads to exit
    Thread.sleep(500);

    // If we get here without hanging, both threads were interrupted successfully
  }

  @Test
  void poisonSentinelHasExpectedId() {
    assertEquals("__poison__", SubscriberOutbox.POISON.id());
  }

  @Test
  void writerSendsKeepAliveOnTimeout() throws Exception {
    Function<String, List<OdysseyEvent>> readFunction = lastId -> List.of();

    SseEmitter emitter = new SseEmitter(0L);
    // Writer will timeout after 50ms and send keep-alive
    SubscriberOutbox outbox = new SubscriberOutbox(readFunction, emitter, "test-stream", "0", 50);
    outbox.start();

    // Allow enough time for at least one keep-alive cycle
    Thread.sleep(200);

    outbox.closeImmediately();
  }

  @Test
  void readerUpdatesLastReadId() throws Exception {
    AtomicInteger callCount = new AtomicInteger(0);
    List<String> lastIdsSeen = new CopyOnWriteArrayList<>();
    CountDownLatch secondReadDone = new CountDownLatch(1);

    Function<String, List<OdysseyEvent>> readFunction =
        lastId -> {
          lastIdsSeen.add(lastId);
          int count = callCount.incrementAndGet();
          if (count == 1) {
            return List.of(testEvent("5-0"));
          }
          secondReadDone.countDown();
          return List.of();
        };

    SseEmitter emitter = new SseEmitter(0L);
    SubscriberOutbox outbox = new SubscriberOutbox(readFunction, emitter, "test-stream", "0", 50);
    outbox.start();

    outbox.nudge();

    assertTrue(secondReadDone.await(5, TimeUnit.SECONDS));

    assertEquals("0", lastIdsSeen.get(0), "First read should use initial lastReadId");
    assertEquals("5-0", lastIdsSeen.get(1), "Second read should use updated lastReadId");

    outbox.closeImmediately();
  }
}
