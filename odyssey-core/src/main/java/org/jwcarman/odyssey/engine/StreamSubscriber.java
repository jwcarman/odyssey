package org.jwcarman.odyssey.engine;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.StreamEventHandler;
import org.jwcarman.odyssey.spi.OdysseyEventLog;

class StreamSubscriber {

  static final OdysseyEvent POISON =
      OdysseyEvent.builder().id("__poison__").streamKey("__poison__").build();

  private final Semaphore nudge = new Semaphore(0);
  private final BlockingQueue<OdysseyEvent> queue = new LinkedBlockingQueue<>();
  private final StreamReader reader;
  private final StreamWriter writer;
  private final String streamKey;

  private Thread readerThread;
  private Thread writerThread;

  StreamSubscriber(
      OdysseyEventLog eventLog,
      StreamEventHandler handler,
      String streamKey,
      String lastReadId,
      long keepAliveInterval) {
    this.streamKey = streamKey;
    this.reader =
        new StreamReader(eventLog, streamKey, nudge, queue, lastReadId, keepAliveInterval);
    this.writer = new StreamWriter(queue, handler, keepAliveInterval);
  }

  void enqueue(OdysseyEvent event) {
    queue.offer(event);
  }

  void start() {
    readerThread = Thread.ofVirtual().name("odyssey-reader-" + streamKey).start(reader);
    writerThread = Thread.ofVirtual().name("odyssey-writer-" + streamKey).start(writer);
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
}
