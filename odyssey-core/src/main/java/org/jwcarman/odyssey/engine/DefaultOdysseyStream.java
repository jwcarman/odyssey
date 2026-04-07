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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.substrate.core.Journal;
import org.jwcarman.substrate.core.JournalCursor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class DefaultOdysseyStream implements OdysseyStream {

  record StreamConfig(long keepAliveInterval, long defaultSseTimeout) {}

  private final Journal<OdysseyEvent> journal;
  private final StreamConfig config;
  private final ObjectMapper objectMapper;
  private final List<StreamSubscription> activeSubscriptions = new CopyOnWriteArrayList<>();

  DefaultOdysseyStream(
      Journal<OdysseyEvent> journal, StreamConfig config, ObjectMapper objectMapper) {
    this.journal = journal;
    this.config = config;
    this.objectMapper = objectMapper;
  }

  @Override
  public String publishRaw(String payload) {
    return publishRaw(null, payload);
  }

  @Override
  public String publishRaw(String eventType, String payload) {
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType(eventType)
            .payload(payload)
            .timestamp(Instant.now())
            .metadata(Map.of())
            .build();
    return journal.append(event);
  }

  @Override
  public String publishJson(Object payload) {
    return publishJson(null, payload);
  }

  @Override
  public String publishJson(String eventType, Object payload) {
    String json = objectMapper.writeValueAsString(payload);
    return publishRaw(eventType, json);
  }

  @Override
  public SseEmitter subscribe() {
    return subscribe(Duration.ofMillis(config.defaultSseTimeout()));
  }

  @Override
  public SseEmitter subscribe(Duration timeout) {
    JournalCursor<OdysseyEvent> cursor = journal.read();
    return createSubscription(cursor, timeout);
  }

  @Override
  public SseEmitter resumeAfter(String lastEventId) {
    return resumeAfter(lastEventId, Duration.ofMillis(config.defaultSseTimeout()));
  }

  @Override
  public SseEmitter resumeAfter(String lastEventId, Duration timeout) {
    JournalCursor<OdysseyEvent> cursor = journal.readAfter(lastEventId);
    return createSubscription(cursor, timeout);
  }

  @Override
  public SseEmitter replayLast(int count) {
    return replayLast(count, Duration.ofMillis(config.defaultSseTimeout()));
  }

  @Override
  public SseEmitter replayLast(int count, Duration timeout) {
    JournalCursor<OdysseyEvent> cursor = journal.readLast(count);
    return createSubscription(cursor, timeout);
  }

  @Override
  public void close() {
    journal.complete();
  }

  @Override
  public void delete() {
    for (StreamSubscription sub : activeSubscriptions) {
      sub.close();
    }
    journal.delete();
  }

  @Override
  public String getStreamKey() {
    return journal.key();
  }

  private SseEmitter createSubscription(JournalCursor<OdysseyEvent> cursor, Duration timeout) {
    SseEmitter emitter = new SseEmitter(timeout.toMillis());
    AtomicReference<StreamSubscription> subscriptionRef = new AtomicReference<>();
    Runnable cleanup =
        () -> {
          StreamSubscription sub = subscriptionRef.get();
          if (sub != null) {
            activeSubscriptions.remove(sub);
            sub.close();
          }
        };
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);
    StreamSubscription subscription =
        new StreamSubscription(cursor, handler, journal.key(), config.keepAliveInterval());
    subscriptionRef.set(subscription);
    activeSubscriptions.add(subscription);
    subscription.start();
    return emitter;
  }
}
