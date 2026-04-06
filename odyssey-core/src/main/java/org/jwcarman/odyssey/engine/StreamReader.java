package org.jwcarman.odyssey.engine;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.OdysseyEventLog;

class StreamReader implements Runnable {

  private final OdysseyEventLog eventLog;
  private final String streamKey;
  private final Semaphore nudge;
  private final BlockingQueue<OdysseyEvent> queue;

  private String lastReadId;

  StreamReader(
      OdysseyEventLog eventLog,
      String streamKey,
      Semaphore nudge,
      BlockingQueue<OdysseyEvent> queue,
      String lastReadId) {
    this.eventLog = eventLog;
    this.streamKey = streamKey;
    this.nudge = nudge;
    this.queue = queue;
    this.lastReadId = lastReadId;
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        nudge.acquire();
        nudge.drainPermits();
        List<OdysseyEvent> events = eventLog.readAfter(streamKey, lastReadId).toList();
        for (OdysseyEvent event : events) {
          queue.put(event);
          lastReadId = event.id();
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
