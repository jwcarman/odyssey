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

import java.time.Duration;
import java.util.Optional;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.StreamEventHandler;
import org.jwcarman.substrate.core.JournalCursor;
import org.jwcarman.substrate.core.JournalEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StreamSubscription {

  private static final Logger log = LoggerFactory.getLogger(StreamSubscription.class);

  private final JournalCursor<OdysseyEvent> cursor;
  private final long keepAliveInterval;
  private final String streamKey;

  private StreamEventHandler handler;
  private Thread writerThread;

  StreamSubscription(JournalCursor<OdysseyEvent> cursor, String streamKey, long keepAliveInterval) {
    this.cursor = cursor;
    this.streamKey = streamKey;
    this.keepAliveInterval = keepAliveInterval;
  }

  void start(StreamEventHandler handler) {
    this.handler = handler;
    log.debug("[{}] Starting writer thread", streamKey);
    writerThread = Thread.ofVirtual().name("odyssey-writer-" + streamKey).start(this::writerLoop);
  }

  void close() {
    log.debug("[{}] Closing subscription", streamKey);
    cursor.close();
    if (writerThread != null) {
      writerThread.interrupt();
    }
  }

  private void writerLoop() {
    log.debug(
        "[{}] Writer thread started, polling with {}ms interval", streamKey, keepAliveInterval);
    try {
      while (cursor.isOpen()) {
        Optional<JournalEntry<OdysseyEvent>> entry =
            cursor.poll(Duration.ofMillis(keepAliveInterval));
        if (entry.isPresent()) {
          log.debug("[{}] Received entry id={}", streamKey, entry.get().id());
          handler.onEvent(toOdysseyEvent(entry.get()));
        } else if (cursor.isOpen()) {
          log.trace("[{}] Poll timeout, sending keep-alive", streamKey);
          handler.onKeepAlive();
        }
      }
      log.debug("[{}] Cursor closed, completing stream", streamKey);
      handler.onComplete();
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        log.debug("[{}] Writer thread interrupted", streamKey);
        Thread.currentThread().interrupt();
      } else {
        log.debug("[{}] Writer thread error", streamKey, e);
        handler.onError(e);
      }
    }
  }

  private static OdysseyEvent toOdysseyEvent(JournalEntry<OdysseyEvent> entry) {
    OdysseyEvent data = entry.data();
    return OdysseyEvent.builder()
        .id(entry.id())
        .streamKey(entry.key())
        .eventType(data.eventType())
        .payload(data.payload())
        .timestamp(entry.timestamp())
        .metadata(data.metadata())
        .build();
  }
}
