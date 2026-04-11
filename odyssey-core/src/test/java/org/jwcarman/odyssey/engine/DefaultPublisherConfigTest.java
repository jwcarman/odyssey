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

class DefaultPublisherConfigTest {

  @Test
  void hardcodedDefaultsArePresent() {
    DefaultPublisherConfig config = new DefaultPublisherConfig();

    assertThat(config.inactivityTtl()).isEqualTo(Duration.ofHours(1));
    assertThat(config.entryTtl()).isEqualTo(Duration.ofHours(1));
    assertThat(config.retentionTtl()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void inactivityTtlSetterReturnsThisAndPersists() {
    DefaultPublisherConfig config = new DefaultPublisherConfig();
    Duration value = Duration.ofDays(1);

    assertThat(config.inactivityTtl(value)).isSameAs(config);
    assertThat(config.inactivityTtl()).isEqualTo(value);
  }

  @Test
  void entryTtlSetterReturnsThisAndPersists() {
    DefaultPublisherConfig config = new DefaultPublisherConfig();
    Duration value = Duration.ofHours(12);

    assertThat(config.entryTtl(value)).isSameAs(config);
    assertThat(config.entryTtl()).isEqualTo(value);
  }

  @Test
  void retentionTtlSetterReturnsThisAndPersists() {
    DefaultPublisherConfig config = new DefaultPublisherConfig();
    Duration value = Duration.ofMinutes(42);

    assertThat(config.retentionTtl(value)).isSameAs(config);
    assertThat(config.retentionTtl()).isEqualTo(value);
  }
}
