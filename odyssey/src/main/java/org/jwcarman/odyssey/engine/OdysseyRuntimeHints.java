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

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers Jackson binding hints for {@link StoredEvent}, odyssey's wire envelope. Substrate's
 * journal hands {@code StoredEvent} instances to the configured {@code Codec<StoredEvent>}
 * (typically {@code codec-jackson}); without these hints Jackson cannot see the record's components
 * in a GraalVM native image and every SSE emit/consume fails.
 *
 * <p>Lives in the same package as {@link StoredEvent} so the registrar can reference the
 * package-private record directly without leaking it into odyssey's public API. Registered via
 * {@code META-INF/spring/aot.factories}.
 */
public class OdysseyRuntimeHints implements RuntimeHintsRegistrar {

  private static final BindingReflectionHintsRegistrar BINDING =
      new BindingReflectionHintsRegistrar();

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    BINDING.registerReflectionHints(hints.reflection(), StoredEvent.class);
  }
}
