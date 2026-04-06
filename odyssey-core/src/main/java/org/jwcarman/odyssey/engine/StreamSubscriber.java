package org.jwcarman.odyssey.engine;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.StreamEventHandler;
import org.jwcarman.odyssey.spi.OdysseyEventLog;

class StreamSubscriber {

  static final OdysseyEvent POISON =
      OdysseyEvent.builder().id("__poison__").streamKey("__poison__").build();

  private final Semaphore nudge = new Semaphore(0);
  private final BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
  private final OdysseyEventLog eventLog;
  private final StreamEventHandler handler;
  private final String streamKey;
  private final long keepAliveInterval;

  private String lastReadId;
  private Thread readerThread;
  private Thread writerThread;

  StreamSubscriber(
      OdysseyEventLog eventLog,
      StreamEventHandler handler,
      String streamKey,
      String lastReadId,
      long keepAliveInterval) {
    this.eventLog = eventLog;
    this.handler = handler;
    this.streamKey = streamKey;
    this.lastReadId = lastReadId;
    this.keepAliveInterval = keepAliveInterval;
  }

  void enqueue(OdysseyEvent event) {
    queue.offer(event);
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
        List<OdysseyEvent> events = eventLog.readAfter(streamKey, lastReadId).toList();
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
          handler.onKeepAlive();
        } else if (event == POISON) {
          drainRemaining();
          handler.onComplete();
          return;
        } else {
          handler.onEvent(event);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      handler.onError(e);
    }
  }

  private void drainRemaining() {
    OdysseyEvent event;
    while ((event = queue.poll()) != null) {
      if (event == POISON) {
        break;
      }
      handler.onEvent(event);
    }
  }
}
