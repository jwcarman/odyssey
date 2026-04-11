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
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jwcarman.odyssey.autoconfigure.OdysseyProperties;
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.OdysseyPublisher;
import org.jwcarman.odyssey.core.PublisherConfig;
import org.jwcarman.odyssey.core.PublisherCustomizer;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.odyssey.core.SubscriberConfig;
import org.jwcarman.odyssey.core.SubscriberCustomizer;
import org.jwcarman.substrate.BlockingSubscription;
import org.jwcarman.substrate.journal.Journal;
import org.jwcarman.substrate.journal.JournalAlreadyExistsException;
import org.jwcarman.substrate.journal.JournalEntry;
import org.jwcarman.substrate.journal.JournalFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

public class DefaultOdyssey implements Odyssey {

  private final JournalFactory journalFactory;
  private final ObjectMapper objectMapper;
  private final OdysseyProperties properties;
  private final List<PublisherCustomizer> publisherCustomizers;
  private final List<SubscriberCustomizer> subscriberCustomizers;

  public DefaultOdyssey(
      JournalFactory journalFactory,
      ObjectMapper objectMapper,
      OdysseyProperties properties,
      List<PublisherCustomizer> publisherCustomizers,
      List<SubscriberCustomizer> subscriberCustomizers) {
    this.journalFactory = journalFactory;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.publisherCustomizers = publisherCustomizers;
    this.subscriberCustomizers = subscriberCustomizers;
  }

  @Override
  public <T> OdysseyPublisher<T> publisher(String key, Class<T> type) {
    return publisher(key, type, cfg -> {});
  }

  @Override
  public <T> OdysseyPublisher<T> publisher(
      String key, Class<T> type, Consumer<PublisherConfig> customizer) {
    return createPublisher(key, type, cfg -> {}, customizer);
  }

  @Override
  public <T> OdysseyPublisher<T> ephemeral(Class<T> type) {
    return ephemeral(type, cfg -> {});
  }

  @Override
  public <T> OdysseyPublisher<T> ephemeral(Class<T> type, Consumer<PublisherConfig> customizer) {
    String key = "ephemeral:" + UUID.randomUUID();
    Duration ttl = properties.ephemeralTtl();
    return createPublisher(key, type, categoryTtl(ttl), customizer);
  }

  @Override
  public <T> OdysseyPublisher<T> channel(String name, Class<T> type) {
    return channel(name, type, cfg -> {});
  }

  @Override
  public <T> OdysseyPublisher<T> channel(
      String name, Class<T> type, Consumer<PublisherConfig> customizer) {
    String key = "channel:" + name;
    Duration ttl = properties.channelTtl();
    return createPublisher(key, type, categoryTtl(ttl), customizer);
  }

  @Override
  public <T> OdysseyPublisher<T> broadcast(String name, Class<T> type) {
    return broadcast(name, type, cfg -> {});
  }

  @Override
  public <T> OdysseyPublisher<T> broadcast(
      String name, Class<T> type, Consumer<PublisherConfig> customizer) {
    String key = "broadcast:" + name;
    Duration ttl = properties.broadcastTtl();
    return createPublisher(key, type, categoryTtl(ttl), customizer);
  }

  @Override
  public <T> SseEmitter subscribe(String key, Class<T> type) {
    return subscribe(key, type, cfg -> {});
  }

  @Override
  public <T> SseEmitter subscribe(
      String key, Class<T> type, Consumer<SubscriberConfig<T>> customizer) {
    DefaultSubscriberConfig<T> config = createSubscriberConfig(customizer);
    Journal<StoredEvent> journal = journalFactory.connect(key, StoredEvent.class);
    return startAdapter(key, type, config, journal::subscribe);
  }

  @Override
  public <T> SseEmitter resume(String key, Class<T> type, String lastEventId) {
    return resume(key, type, lastEventId, cfg -> {});
  }

  @Override
  public <T> SseEmitter resume(
      String key, Class<T> type, String lastEventId, Consumer<SubscriberConfig<T>> customizer) {
    DefaultSubscriberConfig<T> config = createSubscriberConfig(customizer);
    Journal<StoredEvent> journal = journalFactory.connect(key, StoredEvent.class);
    return startAdapter(key, type, config, () -> journal.subscribeAfter(lastEventId));
  }

  @Override
  public <T> SseEmitter replay(String key, Class<T> type, int count) {
    return replay(key, type, count, cfg -> {});
  }

  @Override
  public <T> SseEmitter replay(
      String key, Class<T> type, int count, Consumer<SubscriberConfig<T>> customizer) {
    DefaultSubscriberConfig<T> config = createSubscriberConfig(customizer);
    Journal<StoredEvent> journal = journalFactory.connect(key, StoredEvent.class);
    return startAdapter(key, type, config, () -> journal.subscribeLast(count));
  }

  private <T> OdysseyPublisher<T> createPublisher(
      String key,
      Class<T> type,
      Consumer<PublisherConfig> categoryCustomizer,
      Consumer<PublisherConfig> callCustomizer) {
    DefaultPublisherConfig config = new DefaultPublisherConfig();
    categoryCustomizer.accept(config);
    publisherCustomizers.forEach(c -> c.accept(config));
    callCustomizer.accept(config);

    Journal<StoredEvent> journal = createOrConnect(key, config.inactivityTtl());
    return new DefaultOdysseyPublisher<>(
        journal, objectMapper, config.entryTtl(), config.retentionTtl());
  }

  private Journal<StoredEvent> createOrConnect(String key, Duration inactivityTtl) {
    try {
      return journalFactory.create(key, StoredEvent.class, inactivityTtl);
    } catch (JournalAlreadyExistsException e) {
      return journalFactory.connect(key, StoredEvent.class);
    }
  }

  private <T> DefaultSubscriberConfig<T> createSubscriberConfig(
      Consumer<SubscriberConfig<T>> customizer) {
    SseEventMapper<T> defaultMapper = SseEventMapper.defaultMapper(objectMapper);
    DefaultSubscriberConfig<T> config = new DefaultSubscriberConfig<>(defaultMapper);
    config.timeout(properties.sseTimeout());
    config.keepAliveInterval(properties.keepAliveInterval());
    subscriberCustomizers.forEach(c -> c.accept(config));
    customizer.accept(config);
    return config;
  }

  private <T> SseEmitter startAdapter(
      String key,
      Class<T> type,
      DefaultSubscriberConfig<T> config,
      Supplier<BlockingSubscription<JournalEntry<StoredEvent>>> sourceSupplier) {
    SseEmitter emitter = new SseEmitter(config.timeout().toMillis());
    SseJournalAdapter<T> adapter =
        new SseJournalAdapter<>(sourceSupplier, emitter, key, config, objectMapper, type);
    adapter.start();
    return emitter;
  }

  private static Consumer<PublisherConfig> categoryTtl(Duration ttl) {
    return cfg -> cfg.inactivityTtl(ttl).entryTtl(ttl);
  }
}
