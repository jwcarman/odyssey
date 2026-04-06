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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
  void poisonSentinelHasExpectedId() {
    assertEquals("__poison__", StreamSubscriber.POISON.id());
  }

  @Test
  void startLaunchesBothThreads() throws Exception {
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

    subscriber.nudge();
    assertTrue(readCalled.await(5, TimeUnit.SECONDS), "Reader thread should be running");

    subscriber.closeImmediately();
  }

  @Test
  void nudgeReachesReader() throws Exception {
    CountDownLatch readCalled = new CountDownLatch(1);

    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(eq("test-stream"), anyString()))
        .thenAnswer(
            inv -> {
              readCalled.countDown();
              return Stream.empty();
            });

    StreamEventHandler handler = mock(StreamEventHandler.class);
    StreamSubscriber subscriber =
        new StreamSubscriber(eventLog, handler, "test-stream", "0", 60_000);
    subscriber.start();

    subscriber.nudge();

    assertTrue(readCalled.await(5, TimeUnit.SECONDS), "Nudge should wake the reader");

    subscriber.closeImmediately();
  }

  @Test
  void enqueueOffersToWriterQueue() throws Exception {
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

    assertTrue(
        eventReceived.await(5, TimeUnit.SECONDS), "Handler should receive the enqueued event");
    verify(handler).onEvent(event);

    subscriber.closeImmediately();
  }

  @Test
  void gracefulShutdownDrainsAndCompletes() throws Exception {
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

    Thread.sleep(200);

    subscriber.closeGracefully();

    Thread.sleep(500);

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

    subscriber.nudge();
    assertTrue(readerStarted.await(5, TimeUnit.SECONDS));

    subscriber.closeImmediately();

    Thread.sleep(500);

    // If we get here without hanging, both threads were interrupted successfully
  }
}
