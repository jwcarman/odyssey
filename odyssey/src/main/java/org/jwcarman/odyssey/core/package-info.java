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
/**
 * Odyssey public API. Everything in this package is intended for use by application code and
 * framework authors building on top of Odyssey.
 *
 * <p>Entry point is {@link org.jwcarman.odyssey.core.Odyssey}, the Spring-managed facade that hands
 * out {@link org.jwcarman.odyssey.core.OdysseyStream} handles. Each handle carries a stream's name,
 * element type, and TTL policy, and exposes publish, subscribe, resume, replay, complete, and
 * delete as methods. Per-subscription configuration is customizer-driven: pass a {@link
 * org.jwcarman.odyssey.core.SubscriberCustomizer} lambda to any subscribe/resume/replay call and
 * the library owns the lifecycle. App-wide defaults come from {@code odyssey.*} properties; there
 * is no global customizer-bean mechanism.
 *
 * <p>Mappers receive a {@link org.jwcarman.odyssey.core.DeliveredEvent} per value and may
 * optionally emit terminal SSE frames via {@link
 * org.jwcarman.odyssey.core.SseEventMapper#terminal(org.jwcarman.odyssey.core.SseEventMapper.TerminalState)}.
 *
 * <p>Odyssey's internals (publisher/subscriber implementations, writer-loop adapter, package-
 * private event wrappers) live in {@code org.jwcarman.odyssey.engine} and are not part of the
 * public API.
 */
package org.jwcarman.odyssey.core;
