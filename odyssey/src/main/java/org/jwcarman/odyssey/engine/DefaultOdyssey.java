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

import org.jwcarman.odyssey.autoconfigure.OdysseyProperties;
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.TtlPolicy;
import org.jwcarman.substrate.journal.Journal;
import org.jwcarman.substrate.journal.JournalAlreadyExistsException;
import org.jwcarman.substrate.journal.JournalFactory;
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
  public <T> OdysseyStream<T> stream(String name, Class<T> type) {
    return stream(name, type, properties.defaultTtl());
  }

  @Override
  public <T> OdysseyStream<T> stream(String name, Class<T> type, TtlPolicy ttl) {
    Journal<StoredEvent> journal = createOrConnect(name, ttl);
    return new DefaultOdysseyStream<>(journal, name, type, ttl, objectMapper, properties);
  }

  private Journal<StoredEvent> createOrConnect(String name, TtlPolicy ttl) {
    try {
      return journalFactory.create(name, StoredEvent.class, ttl.inactivityTtl());
    } catch (JournalAlreadyExistsException _) {
      return journalFactory.connect(name, StoredEvent.class);
    }
  }
}
