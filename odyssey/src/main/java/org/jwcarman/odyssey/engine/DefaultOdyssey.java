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
import java.util.function.Supplier;
import org.jwcarman.odyssey.autoconfigure.OdysseyProperties;
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.OdysseyPublisher;
import org.jwcarman.odyssey.core.PublisherCustomizer;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.odyssey.core.SubscriberCustomizer;
import org.jwcarman.odyssey.core.TtlPolicy;
import org.jwcarman.substrate.BlockingSubscription;
import org.jwcarman.substrate.journal.Journal;
import org.jwcarman.substrate.journal.JournalAlreadyExistsException;
import org.jwcarman.substrate.journal.JournalEntry;
import org.jwcarman.substrate.journal.JournalFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * Default {@link Odyssey} implementation. Instantiated by {@link
 * org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration}; applications should depend on the
 * {@code Odyssey} interface rather than this class directly.
 */
public class DefaultOdyssey implements Odyssey {

  private final JournalFactory journalFactory;
  private final ObjectMapper objectMapper;
  private final OdysseyProperties properties;

  /**
   * Creates a new {@code DefaultOdyssey} wired with the given collaborators.
   *
   * @param journalFactory the Substrate journal factory used to create/connect journals
   * @param objectMapper the Jackson mapper used for typed event serialization
   * @param properties the Odyssey configuration properties bound from {@code odyssey.*}
   */
  public DefaultOdyssey(
      JournalFactory journalFactory, ObjectMapper objectMapper, OdysseyProperties properties) {
    this.journalFactory = journalFactory;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public <T> OdysseyPublisher<T> publisher(String name, Class<T> type) {
    return publisher(name, type, cfg -> {});
  }

  @Override
  public <T> OdysseyPublisher<T> publisher(
      String name, Class<T> type, PublisherCustomizer customizer) {
    DefaultPublisherConfig config = new DefaultPublisherConfig();
    config.ttl(properties.defaultTtl());
    customizer.accept(config);

    TtlPolicy ttl = config.ttl();
    Journal<StoredEvent> journal = createOrConnect(name, ttl.inactivityTtl());
    return new DefaultOdysseyPublisher<>(journal, name, objectMapper, ttl);
  }

  @Override
  public <T> SseEmitter subscribe(String name, Class<T> type) {
    return subscribe(name, type, cfg -> {});
  }

  @Override
  public <T> SseEmitter subscribe(String name, Class<T> type, SubscriberCustomizer<T> customizer) {
    DefaultSubscriberConfig<T> config = createSubscriberConfig(customizer);
    Journal<StoredEvent> journal = journalFactory.connect(name, StoredEvent.class);
    return startAdapter(name, type, config, journal::subscribe);
  }

  @Override
  public <T> SseEmitter resume(String name, Class<T> type, String lastEventId) {
    return resume(name, type, lastEventId, cfg -> {});
  }

  @Override
  public <T> SseEmitter resume(
      String name, Class<T> type, String lastEventId, SubscriberCustomizer<T> customizer) {
    DefaultSubscriberConfig<T> config = createSubscriberConfig(customizer);
    Journal<StoredEvent> journal = journalFactory.connect(name, StoredEvent.class);
    return startAdapter(name, type, config, () -> journal.subscribeAfter(lastEventId));
  }

  @Override
  public <T> SseEmitter replay(String name, Class<T> type, int count) {
    return replay(name, type, count, cfg -> {});
  }

  @Override
  public <T> SseEmitter replay(
      String name, Class<T> type, int count, SubscriberCustomizer<T> customizer) {
    DefaultSubscriberConfig<T> config = createSubscriberConfig(customizer);
    Journal<StoredEvent> journal = journalFactory.connect(name, StoredEvent.class);
    return startAdapter(name, type, config, () -> journal.subscribeLast(count));
  }

  private Journal<StoredEvent> createOrConnect(String name, Duration inactivityTtl) {
    try {
      return journalFactory.create(name, StoredEvent.class, inactivityTtl);
    } catch (JournalAlreadyExistsException _) {
      return journalFactory.connect(name, StoredEvent.class);
    }
  }

  private <T> DefaultSubscriberConfig<T> createSubscriberConfig(
      SubscriberCustomizer<T> customizer) {
    SseEventMapper<T> defaultMapper = SseEventMapper.defaultMapper(objectMapper);
    DefaultSubscriberConfig<T> config = new DefaultSubscriberConfig<>(defaultMapper);
    config.timeout(properties.sse().timeout());
    config.keepAliveInterval(properties.sse().keepAlive());
    customizer.accept(config);
    return config;
  }

  private <T> SseEmitter startAdapter(
      String name,
      Class<T> type,
      DefaultSubscriberConfig<T> config,
      Supplier<BlockingSubscription<JournalEntry<StoredEvent>>> sourceSupplier) {
    SseEmitter emitter = new SseEmitter(config.timeout().toMillis());
    SseJournalAdapter.launch(sourceSupplier, emitter, name, config, objectMapper, type);
    return emitter;
  }
}
