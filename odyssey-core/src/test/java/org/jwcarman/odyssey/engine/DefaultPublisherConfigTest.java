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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.TtlPolicy;

class DefaultPublisherConfigTest {

  @Test
  void hardcodedDefaultsArePresent() {
    DefaultPublisherConfig config = new DefaultPublisherConfig();
    TtlPolicy ttl = config.ttl();

    assertThat(ttl.inactivityTtl()).isEqualTo(Duration.ofHours(1));
    assertThat(ttl.entryTtl()).isEqualTo(Duration.ofHours(1));
    assertThat(ttl.retentionTtl()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void ttlSetterReplacesAllThreeFields() {
    DefaultPublisherConfig config = new DefaultPublisherConfig();
    TtlPolicy newTtl =
        new TtlPolicy(Duration.ofDays(1), Duration.ofHours(12), Duration.ofMinutes(30));

    assertThat(config.ttl(newTtl)).isSameAs(config);
    // The interface default method routes through the three individual setters, which
    // rebuild the TtlPolicy via withX methods -- so the result is equal but not the
    // same instance.
    assertThat(config.ttl()).isEqualTo(newTtl);
  }

  @Test
  void inactivityTtlSetterReturnsThisAndUpdatesOnlyThatField() {
    DefaultPublisherConfig config = new DefaultPublisherConfig();
    Duration value = Duration.ofDays(1);

    assertThat(config.inactivityTtl(value)).isSameAs(config);
    assertThat(config.ttl().inactivityTtl()).isEqualTo(value);
    assertThat(config.ttl().entryTtl()).isEqualTo(Duration.ofHours(1));
    assertThat(config.ttl().retentionTtl()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void entryTtlSetterReturnsThisAndUpdatesOnlyThatField() {
    DefaultPublisherConfig config = new DefaultPublisherConfig();
    Duration value = Duration.ofHours(12);

    assertThat(config.entryTtl(value)).isSameAs(config);
    assertThat(config.ttl().entryTtl()).isEqualTo(value);
    assertThat(config.ttl().inactivityTtl()).isEqualTo(Duration.ofHours(1));
    assertThat(config.ttl().retentionTtl()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void retentionTtlSetterReturnsThisAndUpdatesOnlyThatField() {
    DefaultPublisherConfig config = new DefaultPublisherConfig();
    Duration value = Duration.ofMinutes(42);

    assertThat(config.retentionTtl(value)).isSameAs(config);
    assertThat(config.ttl().retentionTtl()).isEqualTo(value);
    assertThat(config.ttl().inactivityTtl()).isEqualTo(Duration.ofHours(1));
    assertThat(config.ttl().entryTtl()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void settersChainCorrectly() {
    DefaultPublisherConfig config = new DefaultPublisherConfig();
    TtlPolicy preset =
        new TtlPolicy(Duration.ofHours(2), Duration.ofHours(2), Duration.ofMinutes(10));

    config.ttl(preset).inactivityTtl(Duration.ofDays(7));

    assertThat(config.ttl().inactivityTtl()).isEqualTo(Duration.ofDays(7));
    assertThat(config.ttl().entryTtl()).isEqualTo(Duration.ofHours(2));
    assertThat(config.ttl().retentionTtl()).isEqualTo(Duration.ofMinutes(10));
  }
}
