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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jwcarman.odyssey.core.DeliveredEvent;
import org.jwcarman.odyssey.core.SseEventMapper.TerminalReason;
import org.jwcarman.substrate.BlockingSubscription;
import org.jwcarman.substrate.NextResult;
import org.jwcarman.substrate.journal.JournalEntry;
import org.jwcarman.substrate.journal.JournalExpiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class SseJournalAdapter<T> {

  private static final Logger log = LoggerFactory.getLogger(SseJournalAdapter.class);

  private final Supplier<BlockingSubscription<JournalEntry<StoredEvent>>> sourceSupplier;
  private final SseEmitter emitter;
  private final String streamKey;
  private final DefaultSubscriberConfig<T> config;
  private final ObjectMapper objectMapper;
  private final Class<T> type;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private volatile BlockingSubscription<JournalEntry<StoredEvent>> source;

  SseJournalAdapter(
      Supplier<BlockingSubscription<JournalEntry<StoredEvent>>> sourceSupplier,
      SseEmitter emitter,
      String streamKey,
      DefaultSubscriberConfig<T> config,
      ObjectMapper objectMapper,
      Class<T> type) {
    this.sourceSupplier = sourceSupplier;
    this.emitter = emitter;
    this.streamKey = streamKey;
    this.config = config;
    this.objectMapper = objectMapper;
    this.type = type;
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
    Thread.ofVirtual().name("odyssey-writer-" + streamKey).start(this::writerLoop);
  }

  void close() {
    if (closed.compareAndSet(false, true)) {
      log.debug("[{}] Closing adapter", streamKey);
      BlockingSubscription<JournalEntry<StoredEvent>> s = this.source;
      if (s != null) {
        s.cancel();
      }
    }
  }

  private void writerLoop() {
    log.debug("[{}] Writer thread started", streamKey);
    try {
      BlockingSubscription<JournalEntry<StoredEvent>> sub = sourceSupplier.get();
      this.source = sub;
      sendComment("connected");
      while (sub.isActive()) {
        NextResult<JournalEntry<StoredEvent>> result = sub.next(config.keepAliveInterval());
        switch (result) {
          case NextResult.Value<JournalEntry<StoredEvent>> v -> {
            log.debug("[{}] Sending event id={}", streamKey, v.value().id());
            sendEvent(v.value());
          }
          case NextResult.Timeout<JournalEntry<StoredEvent>> t -> {
            log.trace("[{}] Sending keep-alive", streamKey);
            sendComment("keep-alive");
          }
          case NextResult.Completed<JournalEntry<StoredEvent>> c -> {
            log.debug("[{}] Source completed", streamKey);
            trySendTerminal(TerminalReason.COMPLETED);
            config.onCompleted().run();
            return;
          }
          case NextResult.Expired<JournalEntry<StoredEvent>> e -> {
            log.debug("[{}] Source expired", streamKey);
            trySendTerminal(TerminalReason.EXPIRED);
            config.onExpired().run();
            return;
          }
          case NextResult.Deleted<JournalEntry<StoredEvent>> d -> {
            log.debug("[{}] Source deleted", streamKey);
            trySendTerminal(TerminalReason.DELETED);
            config.onDeleted().run();
            return;
          }
          case NextResult.Errored<JournalEntry<StoredEvent>> err -> {
            log.debug("[{}] Source errored", streamKey, err.cause());
            trySendTerminal(TerminalReason.ERRORED);
            config.onErrored().accept(err.cause());
            return;
          }
        }
      }
    } catch (JournalExpiredException e) {
      log.debug("[{}] Journal expired on subscribe", streamKey);
      trySendTerminal(TerminalReason.EXPIRED);
      config.onExpired().run();
    } catch (IOException clientGone) {
      log.debug("[{}] Client disconnected: {}", streamKey, clientGone.getMessage());
      close();
    } finally {
      emitter.complete();
    }
  }

  private void sendEvent(JournalEntry<StoredEvent> entry) throws IOException {
    StoredEvent stored = entry.data();
    T data = objectMapper.readValue(stored.data(), type);
    DeliveredEvent<T> event =
        new DeliveredEvent<>(
            entry.id(),
            entry.key(),
            entry.timestamp(),
            stored.eventType(),
            data,
            stored.metadata());
    emitter.send(config.mapper().map(event));
  }

  private void sendComment(String comment) throws IOException {
    emitter.send(SseEmitter.event().comment(comment));
  }

  private void trySendTerminal(TerminalReason reason) {
    try {
      config
          .mapper()
          .terminal(reason)
          .ifPresent(
              event -> {
                try {
                  emitter.send(event);
                } catch (IOException e) {
                  log.debug("[{}] Failed to send terminal event: {}", streamKey, e.getMessage());
                }
              });
    } catch (Exception e) {
      log.debug("[{}] Error building terminal event: {}", streamKey, e.getMessage());
    }
  }
}
