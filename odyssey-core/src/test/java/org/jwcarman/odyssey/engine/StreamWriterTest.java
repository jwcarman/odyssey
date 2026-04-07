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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.StreamEventHandler;

class StreamWriterTest {

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
  void sendsEventToHandler() throws Exception {
    OdysseyEvent event = testEvent("1-0");
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    queue.offer(event);
    queue.offer(StreamSubscriber.POISON);

    CountDownLatch eventReceived = new CountDownLatch(1);
    StreamEventHandler handler = mock(StreamEventHandler.class);
    doAnswer(
            inv -> {
              eventReceived.countDown();
              return null;
            })
        .when(handler)
        .onEvent(any());

    StreamWriter writer = new StreamWriter(queue, handler, 60_000);
    Thread thread = Thread.ofVirtual().start(writer);

    assertTrue(eventReceived.await(5, TimeUnit.SECONDS), "Handler should receive the event");
    verify(handler).onEvent(event);

    thread.join(2000);
  }

  @Test
  void sendsKeepAliveOnTimeout() throws Exception {
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    CountDownLatch keepAliveSent = new CountDownLatch(1);

    StreamEventHandler handler = mock(StreamEventHandler.class);
    doAnswer(
            inv -> {
              keepAliveSent.countDown();
              return null;
            })
        .when(handler)
        .onKeepAlive();

    StreamWriter writer = new StreamWriter(queue, handler, 50);
    Thread thread = Thread.ofVirtual().start(writer);

    assertTrue(
        keepAliveSent.await(5, TimeUnit.SECONDS), "Writer should send keep-alive on timeout");

    thread.interrupt();
  }

  @Test
  void poisonTriggersOnComplete() throws Exception {
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    queue.offer(StreamSubscriber.POISON);

    CountDownLatch completeCalled = new CountDownLatch(1);
    StreamEventHandler handler = mock(StreamEventHandler.class);
    doAnswer(
            inv -> {
              completeCalled.countDown();
              return null;
            })
        .when(handler)
        .onComplete();

    StreamWriter writer = new StreamWriter(queue, handler, 60_000);
    Thread thread = Thread.ofVirtual().start(writer);

    assertTrue(completeCalled.await(5, TimeUnit.SECONDS), "Handler onComplete should be called");
    thread.join(2000);

    assertFalse(thread.isAlive(), "Writer should exit after POISON");
  }

  @Test
  void poisonDrainsRemainingEvents() throws Exception {
    OdysseyEvent event1 = testEvent("1-0");
    OdysseyEvent event2 = testEvent("2-0");

    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    queue.offer(event1);
    queue.offer(event2);
    queue.offer(StreamSubscriber.POISON);

    CountDownLatch completeCalled = new CountDownLatch(1);
    StreamEventHandler handler = mock(StreamEventHandler.class);
    doAnswer(
            inv -> {
              completeCalled.countDown();
              return null;
            })
        .when(handler)
        .onComplete();

    StreamWriter writer = new StreamWriter(queue, handler, 60_000);
    Thread thread = Thread.ofVirtual().start(writer);

    assertTrue(completeCalled.await(5, TimeUnit.SECONDS));
    thread.join(2000);

    verify(handler).onEvent(event1);
    verify(handler).onEvent(event2);
    verify(handler).onComplete();
  }

  @Test
  void handlerExceptionTriggersOnError() throws Exception {
    OdysseyEvent event = testEvent("1-0");
    RuntimeException error = new RuntimeException("test error");

    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    queue.offer(event);

    CountDownLatch errorCalled = new CountDownLatch(1);
    StreamEventHandler handler = mock(StreamEventHandler.class);
    doThrow(error).when(handler).onEvent(any());
    doAnswer(
            inv -> {
              errorCalled.countDown();
              return null;
            })
        .when(handler)
        .onError(any());

    StreamWriter writer = new StreamWriter(queue, handler, 60_000);
    Thread thread = Thread.ofVirtual().start(writer);

    assertTrue(errorCalled.await(5, TimeUnit.SECONDS), "Handler onError should be called");
    verify(handler).onError(error);

    thread.join(2000);
    assertFalse(thread.isAlive(), "Writer should exit after error");
  }

  @Test
  void drainRemainingProcessesEventsAfterPoison() throws Exception {
    OdysseyEvent event1 = testEvent("1-0");
    OdysseyEvent event2 = testEvent("2-0");

    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    // Simulate concurrent reader: POISON first, then events after it
    queue.offer(StreamSubscriber.POISON);
    queue.offer(event1);
    queue.offer(event2);

    CountDownLatch completeCalled = new CountDownLatch(1);
    StreamEventHandler handler = mock(StreamEventHandler.class);
    doAnswer(
            inv -> {
              completeCalled.countDown();
              return null;
            })
        .when(handler)
        .onComplete();

    StreamWriter writer = new StreamWriter(queue, handler, 60_000);
    Thread thread = Thread.ofVirtual().start(writer);

    assertTrue(completeCalled.await(5, TimeUnit.SECONDS));
    thread.join(2000);

    // Events after POISON should have been processed by drainRemaining
    verify(handler).onEvent(event1);
    verify(handler).onEvent(event2);
    verify(handler).onComplete();
  }

  @Test
  void drainRemainingStopsAtSecondPoison() throws Exception {
    OdysseyEvent event1 = testEvent("1-0");

    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    queue.offer(StreamSubscriber.POISON);
    queue.offer(event1);
    queue.offer(StreamSubscriber.POISON);
    queue.offer(testEvent("3-0")); // should not be processed

    CountDownLatch completeCalled = new CountDownLatch(1);
    StreamEventHandler handler = mock(StreamEventHandler.class);
    doAnswer(
            inv -> {
              completeCalled.countDown();
              return null;
            })
        .when(handler)
        .onComplete();

    StreamWriter writer = new StreamWriter(queue, handler, 60_000);
    Thread thread = Thread.ofVirtual().start(writer);

    assertTrue(completeCalled.await(5, TimeUnit.SECONDS));
    thread.join(2000);

    verify(handler).onEvent(event1);
    verify(handler, times(1)).onEvent(any());
    verify(handler).onComplete();
  }

  @Test
  void interruptExitsCleanly() throws Exception {
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    StreamEventHandler handler = mock(StreamEventHandler.class);

    StreamWriter writer = new StreamWriter(queue, handler, 60_000);
    Thread thread = Thread.ofVirtual().start(writer);

    await()
        .atMost(5, SECONDS)
        .until(
            () ->
                thread.getState() == Thread.State.WAITING
                    || thread.getState() == Thread.State.TIMED_WAITING);
    thread.interrupt();
    thread.join(2000);

    assertFalse(thread.isAlive(), "Writer thread should exit on interrupt");
  }
}
