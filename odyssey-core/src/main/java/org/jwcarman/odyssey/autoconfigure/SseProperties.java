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

/**
 * SSE-specific configuration grouped under {@code odyssey.sse} in properties. The field names
 * intentionally omit the "sse" prefix because the enclosing {@link OdysseyProperties#sse()}
 * accessor already provides that namespace.
 *
 * @param timeout the {@code SseEmitter} timeout; {@link Duration#ZERO} means no timeout
 * @param keepAlive the interval between keep-alive comments sent to SSE subscribers
 */
public record SseProperties(Duration timeout, Duration keepAlive) {}
