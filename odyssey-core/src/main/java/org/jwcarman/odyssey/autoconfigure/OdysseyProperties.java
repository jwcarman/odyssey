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
package org.jwcarman.odyssey.autoconfigure;

import org.jwcarman.odyssey.core.TtlPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Odyssey, bound under the {@code odyssey} prefix.
 *
 * <p>Two nested groups:
 *
 * <ul>
 *   <li>{@code odyssey.default-ttl.*} -- the default {@link TtlPolicy} applied to every publisher.
 *       Applications that want different TTL policies per stream should define {@link TtlPolicy}
 *       constants in their own code and pass them via the per-call customizer on {@link
 *       org.jwcarman.odyssey.core.Odyssey#publisher(String, Class, java.util.function.Consumer)}.
 *   <li>{@code odyssey.sse.*} -- SSE-specific settings ({@code timeout}, {@code keep-alive}).
 * </ul>
 *
 * @param defaultTtl the default TTL policy applied to every publisher
 * @param sse SSE-specific settings (emitter timeout and keep-alive interval)
 */
@ConfigurationProperties(prefix = "odyssey")
public record OdysseyProperties(TtlPolicy defaultTtl, SseProperties sse) {}
