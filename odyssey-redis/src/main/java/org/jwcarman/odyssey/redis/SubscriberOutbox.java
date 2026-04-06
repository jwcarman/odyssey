package org.jwcarman.odyssey.redis;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SubscriberOutbox {

  static final OdysseyEvent POISON =
      OdysseyEvent.builder().id("__poison__").streamKey("__poison__").build();

  private final Semaphore nudge = new Semaphore(0);
  private final BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
  private final Function<String, List<OdysseyEvent>> readFunction;
  private final SseEmitter emitter;
  private final String streamKey;
  private final long keepAliveInterval;

  private String lastReadId;
  private Thread readerThread;
  private Thread writerThread;

  SubscriberOutbox(
      Function<String, List<OdysseyEvent>> readFunction,
      SseEmitter emitter,
      String streamKey,
      String lastReadId,
      long keepAliveInterval) {
    this.readFunction = readFunction;
    this.emitter = emitter;
    this.streamKey = streamKey;
    this.lastReadId = lastReadId;
    this.keepAliveInterval = keepAliveInterval;
  }

  void start() {
    readerThread = Thread.ofVirtual().name("odyssey-reader-" + streamKey).start(this::readerLoop);
    writerThread = Thread.ofVirtual().name("odyssey-writer-" + streamKey).start(this::writerLoop);
  }

  void nudge() {
    nudge.release();
  }

  void closeGracefully() {
    if (readerThread != null) {
      readerThread.interrupt();
    }
    queue.offer(POISON);
  }

  void closeImmediately() {
    if (readerThread != null) {
      readerThread.interrupt();
    }
    if (writerThread != null) {
      writerThread.interrupt();
    }
  }

  private void readerLoop() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        nudge.tryAcquire(keepAliveInterval, TimeUnit.MILLISECONDS);
        nudge.drainPermits();
        List<OdysseyEvent> events = readFunction.apply(lastReadId);
        for (OdysseyEvent event : events) {
          queue.offer(event);
          lastReadId = event.id();
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void writerLoop() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        OdysseyEvent event = queue.poll(keepAliveInterval, TimeUnit.MILLISECONDS);
        if (event == null) {
          emitter.send(SseEmitter.event().comment("keep-alive"));
        } else if (event == POISON) {
          drainRemaining();
          emitter.complete();
          return;
        } else {
          sendEvent(event);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException e) {
      emitter.completeWithError(e);
    }
  }

  private void drainRemaining() throws IOException {
    OdysseyEvent event;
    while ((event = queue.poll()) != null) {
      if (event == POISON) {
        break;
      }
      sendEvent(event);
    }
  }

  private void sendEvent(OdysseyEvent event) throws IOException {
    emitter.send(SseEmitter.event().id(event.id()).name(event.eventType()).data(event.payload()));
  }
}
