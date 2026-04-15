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

import java.util.Map;
import java.util.function.Supplier;
import org.jwcarman.odyssey.autoconfigure.OdysseyProperties;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.odyssey.core.SubscriberCustomizer;
import org.jwcarman.odyssey.core.TtlPolicy;
import org.jwcarman.substrate.BlockingSubscription;
import org.jwcarman.substrate.journal.Journal;
import org.jwcarman.substrate.journal.JournalEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class DefaultOdysseyStream<T> implements OdysseyStream<T> {

  private static final Logger log = LoggerFactory.getLogger(DefaultOdysseyStream.class);

  private final Journal<StoredEvent> journal;
  private final String name;
  private final Class<T> type;
  private final TtlPolicy ttl;
  private final ObjectMapper objectMapper;
  private final OdysseyProperties properties;

  DefaultOdysseyStream(
      Journal<StoredEvent> journal,
      String name,
      Class<T> type,
      TtlPolicy ttl,
      ObjectMapper objectMapper,
      OdysseyProperties properties) {
    this.journal = journal;
    this.name = name;
    this.type = type;
    this.ttl = ttl;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String publish(T data) {
    return publish(null, data);
  }

  @Override
  public String publish(String eventType, T data) {
    String json = objectMapper.writeValueAsString(data);
    StoredEvent event = new StoredEvent(eventType, json, Map.of());
    String id = journal.append(event, ttl.entryTtl());
    log.debug("[{}] Published event id={} type={}", name, id, eventType);
    return id;
  }

  @Override
  public SseEmitter subscribe() {
    return subscribe(cfg -> {});
  }

  @Override
  public SseEmitter subscribe(SubscriberCustomizer<T> customizer) {
    return startAdapter(customizer, journal::subscribe);
  }

  @Override
  public SseEmitter resume(String lastEventId) {
    return resume(lastEventId, cfg -> {});
  }

  @Override
  public SseEmitter resume(String lastEventId, SubscriberCustomizer<T> customizer) {
    return startAdapter(customizer, () -> journal.subscribeAfter(lastEventId));
  }

  @Override
  public SseEmitter replay(int count) {
    return replay(count, cfg -> {});
  }

  @Override
  public SseEmitter replay(int count, SubscriberCustomizer<T> customizer) {
    return startAdapter(customizer, () -> journal.subscribeLast(count));
  }

  @Override
  public void complete() {
    log.debug("[{}] Completing journal with retention={}", name, ttl.retentionTtl());
    journal.complete(ttl.retentionTtl());
  }

  @Override
  public void delete() {
    log.debug("[{}] Deleting journal", name);
    journal.delete();
  }

  private SseEmitter startAdapter(
      SubscriberCustomizer<T> customizer,
      Supplier<BlockingSubscription<JournalEntry<StoredEvent>>> sourceSupplier) {
    DefaultSubscriberConfig<T> config = buildSubscriberConfig(customizer);
    SseEmitter emitter = new SseEmitter(config.timeout().toMillis());
    SseJournalAdapter.launch(sourceSupplier, emitter, name, config, objectMapper, type);
    return emitter;
  }

  private DefaultSubscriberConfig<T> buildSubscriberConfig(SubscriberCustomizer<T> customizer) {
    SseEventMapper<T> defaultMapper = SseEventMapper.defaultMapper(objectMapper);
    DefaultSubscriberConfig<T> config = new DefaultSubscriberConfig<>(defaultMapper);
    config.timeout(properties.sse().timeout());
    config.keepAliveInterval(properties.sse().keepAlive());
    customizer.accept(config);
    return config;
  }
}
