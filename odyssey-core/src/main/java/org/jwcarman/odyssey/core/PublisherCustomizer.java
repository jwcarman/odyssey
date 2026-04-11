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
 * Marker interface for application-wide publisher defaults. Register a {@code PublisherCustomizer}
 * as a Spring bean and Odyssey will apply it to every publisher construction before the caller's
 * per-call customizer runs -- so callers can still override anything the global customizer set.
 *
 * <p>Resolution order for publisher config:
 *
 * <ol>
 *   <li>Hardcoded defaults (1h inactivity, 1h entry, 5m retention)
 *   <li>Sugared category TTLs (for {@code ephemeral}/{@code channel}/{@code broadcast}) drawn from
 *       {@link org.jwcarman.odyssey.autoconfigure.OdysseyProperties}
 *   <li>All {@code PublisherCustomizer} beans in Spring's order
 *   <li>The caller's per-call {@code Consumer<PublisherConfig>}
 * </ol>
 *
 * <p>Matches Spring Boot's {@code RestClientCustomizer} / {@code WebClientCustomizer} idiom so
 * users who know Spring Boot already know the pattern.
 */
@FunctionalInterface
public interface PublisherCustomizer extends Consumer<PublisherConfig> {}
