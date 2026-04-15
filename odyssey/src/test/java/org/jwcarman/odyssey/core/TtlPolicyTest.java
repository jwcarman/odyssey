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
package org.jwcarman.odyssey.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TtlPolicyTest {

  private static final TtlPolicy BASE =
      new TtlPolicy(Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(3));

  @Test
  void withInactivityTtlReplacesOnlyInactivity() {
    TtlPolicy updated = BASE.withInactivityTtl(Duration.ofHours(1));

    assertThat(updated.inactivityTtl()).isEqualTo(Duration.ofHours(1));
    assertThat(updated.entryTtl()).isEqualTo(BASE.entryTtl());
    assertThat(updated.retentionTtl()).isEqualTo(BASE.retentionTtl());
    assertThat(updated).isNotSameAs(BASE);
  }

  @Test
  void withEntryTtlReplacesOnlyEntry() {
    TtlPolicy updated = BASE.withEntryTtl(Duration.ofHours(2));

    assertThat(updated.inactivityTtl()).isEqualTo(BASE.inactivityTtl());
    assertThat(updated.entryTtl()).isEqualTo(Duration.ofHours(2));
    assertThat(updated.retentionTtl()).isEqualTo(BASE.retentionTtl());
    assertThat(updated).isNotSameAs(BASE);
  }

  @Test
  void withRetentionTtlReplacesOnlyRetention() {
    TtlPolicy updated = BASE.withRetentionTtl(Duration.ofHours(3));

    assertThat(updated.inactivityTtl()).isEqualTo(BASE.inactivityTtl());
    assertThat(updated.entryTtl()).isEqualTo(BASE.entryTtl());
    assertThat(updated.retentionTtl()).isEqualTo(Duration.ofHours(3));
    assertThat(updated).isNotSameAs(BASE);
  }
}
