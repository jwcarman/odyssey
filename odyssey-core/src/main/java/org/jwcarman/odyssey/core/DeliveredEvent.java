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

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A typed event as it arrives at an {@link SseEventMapper}. Produced by Odyssey's writer loop from
 * the underlying Substrate {@code JournalEntry} plus the application-level wrapping that Odyssey
 * layers on top.
 *
 * <p>Users interact with {@code DeliveredEvent} only through an {@link SseEventMapper}
 * implementation. Mapper code reads {@link #id()}, {@link #streamKey()}, {@link #eventType()}, and
 * {@link #data()} to build an {@code SseEventBuilder}.
 *
 * <p>The {@link #metadata()} map is defensively copied and unmodifiable. A {@code null} argument on
 * construction is normalized to an empty map.
 *
 * @param id the monotonic entry id assigned by Substrate (usable as an SSE {@code Last-Event-ID})
 * @param streamKey the fully-qualified stream key this event belongs to
 * @param timestamp the instant the entry was appended
 * @param eventType the user-supplied event type (becomes the SSE {@code event:} name), or {@code
 *     null} if not set at publish time
 * @param data the typed user payload
 * @param metadata additional key/value metadata supplied at publish time (may be empty)
 * @param <T> the typed payload type
 */
public record DeliveredEvent<T>(
    String id,
    String streamKey,
    Instant timestamp,
    String eventType,
    T data,
    Map<String, String> metadata) {

  /** Canonical constructor -- normalizes {@code null} metadata to an immutable empty map. */
  public DeliveredEvent {
    metadata =
        metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
  }
}
