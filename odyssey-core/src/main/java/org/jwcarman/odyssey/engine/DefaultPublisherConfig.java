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
import org.jwcarman.odyssey.core.PublisherConfig;

class DefaultPublisherConfig implements PublisherConfig {

  private static final Duration DEFAULT_INACTIVITY_TTL = Duration.ofHours(1);
  private static final Duration DEFAULT_ENTRY_TTL = Duration.ofHours(1);
  private static final Duration DEFAULT_RETENTION_TTL = Duration.ofMinutes(5);

  private Duration inactivityTtl = DEFAULT_INACTIVITY_TTL;
  private Duration entryTtl = DEFAULT_ENTRY_TTL;
  private Duration retentionTtl = DEFAULT_RETENTION_TTL;

  @Override
  public PublisherConfig inactivityTtl(Duration ttl) {
    this.inactivityTtl = ttl;
    return this;
  }

  @Override
  public PublisherConfig entryTtl(Duration ttl) {
    this.entryTtl = ttl;
    return this;
  }

  @Override
  public PublisherConfig retentionTtl(Duration ttl) {
    this.retentionTtl = ttl;
    return this;
  }

  Duration inactivityTtl() {
    return inactivityTtl;
  }

  Duration entryTtl() {
    return entryTtl;
  }

  Duration retentionTtl() {
    return retentionTtl;
  }
}
