package org.jwcarman.odyssey.engine;

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
  void interruptExitsCleanly() throws Exception {
    BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
    StreamEventHandler handler = mock(StreamEventHandler.class);

    StreamWriter writer = new StreamWriter(queue, handler, 60_000);
    Thread thread = Thread.ofVirtual().start(writer);

    Thread.sleep(50);
    thread.interrupt();
    thread.join(2000);

    assertFalse(thread.isAlive(), "Writer thread should exit on interrupt");
  }
}
