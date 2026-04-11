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

import java.util.function.Consumer;

/**
 * Marker interface for application-wide subscriber defaults. Register a {@code
 * SubscriberCustomizer} as a Spring bean and Odyssey will apply it to every {@code
 * subscribe}/{@code resume}/{@code replay} call before the caller's per-call customizer runs.
 *
 * <p>The wildcard-typed {@code Consumer<SubscriberConfig<?>>} is deliberate: a globally-applied
 * customizer cannot know the specific event type {@code T} for every subscription it will be
 * applied to, so it can only set type-agnostic knobs like {@code timeout}, {@code
 * keepAliveInterval}, and the four terminal-state callbacks. Do not attempt to call {@code
 * mapper(...)} on a {@code SubscriberConfig<?>}; the mapper is a per-call concern and should be set
 * via the per-call customizer overload on {@link Odyssey}.
 */
@FunctionalInterface
public interface SubscriberCustomizer extends Consumer<SubscriberConfig<?>> {}
