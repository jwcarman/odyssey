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
