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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jwcarman.odyssey.core.DeliveredEvent;
import org.jwcarman.odyssey.core.SseEventMapper.TerminalState;
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
    BlockingSubscription<JournalEntry<StoredEvent>> sub;
    try {
      sub = sourceSupplier.get();
    } catch (JournalExpiredException e) {
      log.debug("[{}] Journal expired on subscribe", streamKey);
      trySendTerminal(new TerminalState.Expired());
      config.onExpired().run();
      emitter.complete();
      return;
    }
    this.source = sub;

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
    BlockingSubscription<JournalEntry<StoredEvent>> sub = this.source;
    Throwable erroredCause = null;
    try {
      sendComment("connected");

      boolean running = true;
      while (running && sub.isActive()) {
        NextResult<JournalEntry<StoredEvent>> result = sub.next(config.keepAliveInterval());
        switch (result) {
          case NextResult.Value<JournalEntry<StoredEvent>>(JournalEntry<StoredEvent> entry) -> {
            log.debug("[{}] Sending event id={}", streamKey, entry.id());
            sendEvent(entry);
          }
          case NextResult.Timeout<JournalEntry<StoredEvent>>() -> {
            log.trace("[{}] Sending keep-alive", streamKey);
            sendComment("keep-alive");
          }
          case NextResult.Completed<JournalEntry<StoredEvent>>() -> {
            log.debug("[{}] Source completed", streamKey);
            trySendTerminal(new TerminalState.Completed());
            config.onCompleted().run();
            running = false;
          }
          case NextResult.Expired<JournalEntry<StoredEvent>>() -> {
            log.debug("[{}] Source expired", streamKey);
            trySendTerminal(new TerminalState.Expired());
            config.onExpired().run();
            running = false;
          }
          case NextResult.Deleted<JournalEntry<StoredEvent>>() -> {
            log.debug("[{}] Source deleted", streamKey);
            trySendTerminal(new TerminalState.Deleted());
            config.onDeleted().run();
            running = false;
          }
          case NextResult.Errored<JournalEntry<StoredEvent>>(Throwable cause) -> {
            log.debug("[{}] Source errored", streamKey, cause);
            boolean emitted = trySendTerminal(new TerminalState.Errored(cause));
            config.onErrored().accept(cause);
            if (!emitted) {
              erroredCause = cause;
            }
            running = false;
          }
        }
      }
    } catch (JournalExpiredException e) {
      log.debug("[{}] Journal expired mid-stream", streamKey);
      trySendTerminal(new TerminalState.Expired());
      config.onExpired().run();
    } catch (IOException clientGone) {
      log.debug("[{}] Client disconnected: {}", streamKey, clientGone.getMessage());
      close();
    } catch (Exception unexpected) {
      log.debug("[{}] Unexpected error in writer loop", streamKey, unexpected);
      erroredCause = unexpected;
    }

    if (erroredCause != null) {
      emitter.completeWithError(erroredCause);
    } else {
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

  /**
   * Invoke the user's {@code terminal(TerminalState)} hook and, if it returns a frame, try to send
   * it. Returns {@code true} if a frame was successfully written to the emitter.
   */
  private boolean trySendTerminal(TerminalState state) {
    try {
      Optional<SseEmitter.SseEventBuilder> event = config.mapper().terminal(state);
      if (event.isEmpty()) {
        return false;
      }
      try {
        emitter.send(event.get());
        return true;
      } catch (IOException e) {
        log.debug("[{}] Failed to send terminal event: {}", streamKey, e.getMessage());
        return false;
      }
    } catch (Exception e) {
      log.debug("[{}] Error building terminal event: {}", streamKey, e.getMessage());
      return false;
    }
  }
}
