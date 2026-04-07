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

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for OdySSEy, bound under the {@code odyssey} prefix.
 *
 * <p>Default values are loaded from {@code odyssey-defaults.properties} on the classpath:
 *
 * <ul>
 *   <li>{@code odyssey.keep-alive-interval} — {@code 30s}
 *   <li>{@code odyssey.sse-timeout} — {@code 0} (no timeout)
 * </ul>
 *
 * @param keepAliveInterval the interval between keep-alive comments sent to SSE subscribers and the
 *     poll timeout for cursor reads (default 30 seconds)
 * @param sseTimeout the {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}
 *     timeout; {@link Duration#ZERO} means no timeout (default no timeout)
 */
@ConfigurationProperties(prefix = "odyssey")
public record OdysseyProperties(Duration keepAliveInterval, Duration sseTimeout) {}
