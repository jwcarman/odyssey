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
