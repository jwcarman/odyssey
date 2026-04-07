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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.substrate.core.Journal;
import org.jwcarman.substrate.core.JournalFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Default implementation of {@link OdysseyStreamRegistry} that uses a {@link JournalFactory} to
 * create journals for each stream.
 */
public class DefaultOdysseyStreamRegistry implements OdysseyStreamRegistry {

  private final JournalFactory journalFactory;
  private final ObjectMapper objectMapper;
  private final long keepAliveInterval;
  private final long defaultSseTimeout;

  private final ConcurrentMap<String, DefaultOdysseyStream> cache = new ConcurrentHashMap<>();

  /**
   * Creates a new registry.
   *
   * @param journalFactory the factory for creating journals
   * @param objectMapper the Jackson object mapper for JSON serialization
   * @param keepAliveInterval the keep-alive interval in milliseconds
   * @param defaultSseTimeout the default SSE emitter timeout in milliseconds
   */
  public DefaultOdysseyStreamRegistry(
      JournalFactory journalFactory,
      ObjectMapper objectMapper,
      long keepAliveInterval,
      long defaultSseTimeout) {
    this.journalFactory = journalFactory;
    this.objectMapper = objectMapper;
    this.keepAliveInterval = keepAliveInterval;
    this.defaultSseTimeout = defaultSseTimeout;
  }

  @Override
  public OdysseyStream ephemeral() {
    String name = "ephemeral:" + UUID.randomUUID();
    return createStream(name);
  }

  @Override
  public OdysseyStream channel(String name) {
    String key = "channel:" + name;
    return cache.computeIfAbsent(key, this::createStream);
  }

  @Override
  public OdysseyStream broadcast(String name) {
    String key = "broadcast:" + name;
    return cache.computeIfAbsent(key, this::createStream);
  }

  @Override
  public OdysseyStream stream(String streamKey) {
    return cache.computeIfAbsent(streamKey, this::createStream);
  }

  private DefaultOdysseyStream createStream(String name) {
    Journal<OdysseyEvent> journal = journalFactory.create(name, OdysseyEvent.class);
    return new DefaultOdysseyStream(
        journal,
        new DefaultOdysseyStream.StreamConfig(keepAliveInterval, defaultSseTimeout),
        objectMapper);
  }
}
