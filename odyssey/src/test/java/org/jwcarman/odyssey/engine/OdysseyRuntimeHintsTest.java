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

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.TypeReference;

class OdysseyRuntimeHintsTest {

  private final RuntimeHints hints = new RuntimeHints();

  {
    new OdysseyRuntimeHints().registerHints(hints, getClass().getClassLoader());
  }

  @Test
  void registersBindingHintsForStoredEvent() {
    assertThat(hints.reflection().typeHints())
        .as("expected binding hints for StoredEvent")
        .anyMatch(th -> th.getType().equals(TypeReference.of(StoredEvent.class)));
  }

  @Test
  void storedEventHintExposesRecordAccessors() {
    TypeHint storedEventHint =
        hints
            .reflection()
            .typeHints()
            .filter(th -> th.getType().equals(TypeReference.of(StoredEvent.class)))
            .findFirst()
            .orElseThrow();

    assertThat(storedEventHint.getMemberCategories())
        .as("BindingReflectionHintsRegistrar should expose record constructor and fields")
        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
        .contains(MemberCategory.ACCESS_DECLARED_FIELDS);
  }
}
