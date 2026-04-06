package org.jwcarman.odyssey.engine;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.StreamEventHandler;

class StreamWriter implements Runnable {

  private final BlockingQueue<OdysseyEvent> queue;
  private final StreamEventHandler handler;
  private final long keepAliveInterval;

  StreamWriter(
      BlockingQueue<OdysseyEvent> queue, StreamEventHandler handler, long keepAliveInterval) {
    this.queue = queue;
    this.handler = handler;
    this.keepAliveInterval = keepAliveInterval;
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        OdysseyEvent event = queue.poll(keepAliveInterval, TimeUnit.MILLISECONDS);
        if (event == null) {
          handler.onKeepAlive();
        } else if (event == StreamSubscriber.POISON) {
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
      if (event == StreamSubscriber.POISON) {
        break;
      }
      handler.onEvent(event);
    }
  }
}
