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
import org.jwcarman.odyssey.core.TtlPolicy;

class DefaultPublisherConfig implements PublisherConfig {

  private static final TtlPolicy DEFAULT_TTL =
      new TtlPolicy(Duration.ofHours(1), Duration.ofHours(1), Duration.ofMinutes(5));

  private TtlPolicy ttl = DEFAULT_TTL;

  // Intentionally no override of ttl(TtlPolicy) — the PublisherConfig default method
  // routes through the three individual setters, which is fine for our single-field
  // representation and keeps coverage on the interface default.

  @Override
  public PublisherConfig inactivityTtl(Duration ttl) {
    this.ttl = this.ttl.withInactivityTtl(ttl);
    return this;
  }

  @Override
  public PublisherConfig entryTtl(Duration ttl) {
    this.ttl = this.ttl.withEntryTtl(ttl);
    return this;
  }

  @Override
  public PublisherConfig retentionTtl(Duration ttl) {
    this.ttl = this.ttl.withRetentionTtl(ttl);
    return this;
  }

  TtlPolicy ttl() {
    return ttl;
  }
}
