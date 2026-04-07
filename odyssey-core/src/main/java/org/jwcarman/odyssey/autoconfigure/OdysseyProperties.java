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
 * Configuration properties for Odyssey, bound under the {@code odyssey} prefix.
 *
 * @param keepAliveInterval the interval between keep-alive comments sent to SSE subscribers
 * @param sseTimeout the SseEmitter timeout; zero means no timeout
 * @param ephemeralTtl default TTL for ephemeral stream events
 * @param channelTtl default TTL for channel stream events
 * @param broadcastTtl default TTL for broadcast stream events
 */
@ConfigurationProperties(prefix = "odyssey")
public record OdysseyProperties(
    Duration keepAliveInterval,
    Duration sseTimeout,
    Duration ephemeralTtl,
    Duration channelTtl,
    Duration broadcastTtl) {}
