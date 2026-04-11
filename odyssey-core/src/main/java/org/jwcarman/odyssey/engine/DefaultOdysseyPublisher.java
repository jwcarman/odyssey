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
import org.jwcarman.odyssey.core.OdysseyPublisher;
import org.jwcarman.odyssey.core.TtlPolicy;
import org.jwcarman.substrate.journal.Journal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

class DefaultOdysseyPublisher<T> implements OdysseyPublisher<T> {

  private static final Logger log = LoggerFactory.getLogger(DefaultOdysseyPublisher.class);

  private final Journal<StoredEvent> journal;
  private final String name;
  private final ObjectMapper objectMapper;
  private final TtlPolicy ttl;

  DefaultOdysseyPublisher(
      Journal<StoredEvent> journal, String name, ObjectMapper objectMapper, TtlPolicy ttl) {
    this.journal = journal;
    this.name = name;
    this.objectMapper = objectMapper;
    this.ttl = ttl;
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
  public void complete() {
    log.debug("[{}] Completing journal with retention={}", name, ttl.retentionTtl());
    journal.complete(ttl.retentionTtl());
  }

  @Override
  public void delete() {
    log.debug("[{}] Deleting journal", name);
    journal.delete();
  }

  @Override
  public String name() {
    return name;
  }
}
