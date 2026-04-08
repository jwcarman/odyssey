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
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.odyssey.core.StreamSubscriberBuilder;
import org.jwcarman.substrate.core.Journal;
import org.jwcarman.substrate.core.JournalCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class DefaultOdysseyStream implements OdysseyStream {

  private static final Logger log = LoggerFactory.getLogger(DefaultOdysseyStream.class);

  record StreamConfig(long keepAliveInterval, long defaultSseTimeout, Duration ttl) {}

  private final Journal<OdysseyEvent> journal;
  private final String key;
  private final StreamConfig config;
  private final ObjectMapper objectMapper;
  private final List<StreamSubscription> activeSubscriptions = new CopyOnWriteArrayList<>();

  DefaultOdysseyStream(
      Journal<OdysseyEvent> journal, StreamConfig config, ObjectMapper objectMapper) {
    this.journal = journal;
    this.key = journal.key();
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
    String id = config.ttl() != null ? journal.append(event, config.ttl()) : journal.append(event);
    log.debug("[{}] Published event id={} type={}", key, id, eventType);
    return id;
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
  public StreamSubscriberBuilder subscriber() {
    return new DefaultStreamSubscriberBuilder();
  }

  @Override
  public SseEmitter subscribe() {
    return subscriber().subscribe();
  }

  @Override
  public SseEmitter subscribe(Duration timeout) {
    return subscriber().timeout(timeout).subscribe();
  }

  @Override
  public SseEmitter resumeAfter(String lastEventId) {
    return subscriber().resumeAfter(lastEventId);
  }

  @Override
  public SseEmitter resumeAfter(String lastEventId, Duration timeout) {
    return subscriber().timeout(timeout).resumeAfter(lastEventId);
  }

  @Override
  public SseEmitter replayLast(int count) {
    return subscriber().replayLast(count);
  }

  @Override
  public SseEmitter replayLast(int count, Duration timeout) {
    return subscriber().timeout(timeout).replayLast(count);
  }

  @Override
  public void close() {
    log.debug("[{}] Closing stream", key);
    journal.complete();
  }

  @Override
  public void delete() {
    log.debug("[{}] Deleting stream ({} active subscriptions)", key, activeSubscriptions.size());
    for (StreamSubscription sub : activeSubscriptions) {
      sub.close();
    }
    journal.delete();
  }

  @Override
  public String getStreamKey() {
    return key;
  }

  private SseEmitter createSubscription(
      JournalCursor<OdysseyEvent> cursor, Duration timeout, SseEventMapper mapper) {
    SseEmitter emitter = new SseEmitter(timeout.toMillis());
    StreamSubscription subscription =
        new StreamSubscription(
            cursor,
            emitter,
            journal.key(),
            config.keepAliveInterval(),
            activeSubscriptions,
            mapper);
    activeSubscriptions.add(subscription);
    subscription.start();
    return emitter;
  }

  private class DefaultStreamSubscriberBuilder implements StreamSubscriberBuilder {

    private Duration timeout = Duration.ofMillis(config.defaultSseTimeout());
    private SseEventMapper mapper = SseEventMapper.DEFAULT;

    @Override
    public StreamSubscriberBuilder timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    @Override
    public StreamSubscriberBuilder mapper(SseEventMapper mapper) {
      this.mapper = mapper;
      return this;
    }

    @Override
    public SseEmitter subscribe() {
      log.debug("[{}] New subscriber (live)", key);
      JournalCursor<OdysseyEvent> cursor = journal.read();
      return createSubscription(cursor, timeout, mapper);
    }

    @Override
    public SseEmitter resumeAfter(String lastEventId) {
      log.debug("[{}] New subscriber (resumeAfter {})", key, lastEventId);
      JournalCursor<OdysseyEvent> cursor = journal.readAfter(lastEventId);
      return createSubscription(cursor, timeout, mapper);
    }

    @Override
    public SseEmitter replayLast(int count) {
      log.debug("[{}] New subscriber (replayLast {})", key, count);
      JournalCursor<OdysseyEvent> cursor = journal.readLast(count);
      return createSubscription(cursor, timeout, mapper);
    }
  }
}
