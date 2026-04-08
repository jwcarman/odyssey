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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.substrate.core.JournalCursor;
import org.jwcarman.substrate.core.JournalEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class StreamSubscription {

  private static final Logger log = LoggerFactory.getLogger(StreamSubscription.class);

  private final JournalCursor<OdysseyEvent> cursor;
  private final SseEmitter emitter;
  private final String streamKey;
  private final long keepAliveInterval;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final List<StreamSubscription> subscriptionList;
  private final SseEventMapper mapper;

  StreamSubscription(
      JournalCursor<OdysseyEvent> cursor,
      SseEmitter emitter,
      String streamKey,
      long keepAliveInterval,
      List<StreamSubscription> subscriptionList,
      SseEventMapper mapper) {
    this.cursor = cursor;
    this.emitter = emitter;
    this.streamKey = streamKey;
    this.keepAliveInterval = keepAliveInterval;
    this.subscriptionList = subscriptionList;
    this.mapper = mapper;
  }

  void start() {
    emitter.onCompletion(
        () -> {
          log.debug("[{}] SseEmitter completed", streamKey);
          close();
        });
    emitter.onError(
        e -> {
          log.debug("[{}] SseEmitter error: {}", streamKey, e.getMessage());
          close();
        });
    emitter.onTimeout(
        () -> {
          log.debug("[{}] SseEmitter timed out", streamKey);
          close();
        });
    sendComment("connected");
    log.debug("[{}] Starting writer thread", streamKey);
    Thread.ofVirtual().name("odyssey-writer-" + streamKey).start(this::writerLoop);
  }

  void close() {
    if (closed.compareAndSet(false, true)) {
      log.debug("[{}] Closing subscription", streamKey);
      cursor.close();
      subscriptionList.remove(this);
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
          log.debug("[{}] Sending event id={}", streamKey, entry.get().id());
          sendEvent(toOdysseyEvent(entry.get()));
        } else if (cursor.isOpen()) {
          log.trace("[{}] Sending keep-alive", streamKey);
          sendComment("keep-alive");
        }
      }
      log.debug("[{}] Cursor closed, completing emitter", streamKey);
      emitter.complete();
    } catch (Exception e) {
      log.debug("[{}] Writer thread error", streamKey, e);
      emitter.completeWithError(e);
    }
  }

  private void sendEvent(OdysseyEvent event) {
    send(mapper.map(event));
  }

  private void sendComment(String comment) {
    send(SseEmitter.event().comment(comment));
  }

  private void send(SseEmitter.SseEventBuilder event) {
    try {
      emitter.send(event);
    } catch (IOException e) {
      log.debug("[{}] Send failed (client disconnected?): {}", streamKey, e.getMessage());
      close();
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
