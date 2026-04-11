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

  private final BlockingSubscription<JournalEntry<StoredEvent>> source;
  private final SseEmitter emitter;
  private final String streamKey;
  private final DefaultSubscriberConfig<T> config;
  private final ObjectMapper objectMapper;
  private final Class<T> type;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  SseJournalAdapter(
      BlockingSubscription<JournalEntry<StoredEvent>> source,
      SseEmitter emitter,
      String streamKey,
      DefaultSubscriberConfig<T> config,
      ObjectMapper objectMapper,
      Class<T> type) {
    this.source = source;
    this.emitter = emitter;
    this.streamKey = streamKey;
    this.config = config;
    this.objectMapper = objectMapper;
    this.type = type;
  }

  /**
   * Establish the subscription on the caller's thread and start an adapter that drives the emitter
   * on a virtual writer thread. If the subscription throws {@link JournalExpiredException} during
   * creation, fire the terminal-expired path inline and complete the emitter without spawning a
   * writer thread. This keeps {@link #source} final and non-null for every adapter instance.
   */
  static <T> void launch(
      Supplier<BlockingSubscription<JournalEntry<StoredEvent>>> sourceSupplier,
      SseEmitter emitter,
      String streamKey,
      DefaultSubscriberConfig<T> config,
      ObjectMapper objectMapper,
      Class<T> type) {
    BlockingSubscription<JournalEntry<StoredEvent>> source;
    try {
      source = sourceSupplier.get();
    } catch (JournalExpiredException _) {
      log.debug("[{}] Journal expired on subscribe", streamKey);
      fireTerminalExpired(emitter, config, streamKey);
      return;
    }
    new SseJournalAdapter<>(source, emitter, streamKey, config, objectMapper, type).begin();
  }

  void begin() {
    emitter.onCompletion(
        () -> {
          log.debug("[{}] SseEmitter completed", streamKey);
          close();
        });
    emitter.onError(
        _ -> {
          log.debug("[{}] SseEmitter error", streamKey);
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
      source.cancel();
    }
  }

  private void writerLoop() {
    log.debug("[{}] Writer thread started", streamKey);
    Throwable erroredCause = null;
    try {
      sendComment("connected");

      boolean running = true;
      while (running && source.isActive()) {
        NextResult<JournalEntry<StoredEvent>> result = source.next(config.keepAliveInterval());
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
    } catch (JournalExpiredException _) {
      log.debug("[{}] Journal expired mid-stream", streamKey);
      trySendTerminal(new TerminalState.Expired());
      config.onExpired().run();
    } catch (IOException _) {
      log.debug("[{}] Client disconnected", streamKey);
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
    Optional<SseEmitter.SseEventBuilder> frame;
    try {
      frame = config.mapper().terminal(state);
    } catch (Exception _) {
      log.debug("[{}] Error building terminal event", streamKey);
      return false;
    }
    return frame.map(this::sendTerminalFrame).orElse(false);
  }

  private boolean sendTerminalFrame(SseEmitter.SseEventBuilder frame) {
    try {
      emitter.send(frame);
      return true;
    } catch (IOException _) {
      log.debug("[{}] Failed to send terminal event", streamKey);
      return false;
    }
  }

  /**
   * Static path for the "journal expired at subscribe time" case where no adapter instance exists.
   * Fires the terminal hook, runs the onExpired callback, and completes the emitter. Kept small and
   * local so the instance path stays simple.
   */
  private static <T> void fireTerminalExpired(
      SseEmitter emitter, DefaultSubscriberConfig<T> config, String streamKey) {
    try {
      Optional<SseEmitter.SseEventBuilder> frame =
          config.mapper().terminal(new TerminalState.Expired());
      if (frame.isPresent()) {
        try {
          emitter.send(frame.get());
        } catch (IOException _) {
          log.debug("[{}] Failed to send terminal-expired event", streamKey);
        }
      }
    } catch (Exception _) {
      log.debug("[{}] Error building terminal-expired event", streamKey);
    }
    try {
      config.onExpired().run();
    } catch (Exception _) {
      log.debug("[{}] onExpired callback threw", streamKey);
    }
    emitter.complete();
  }
}
