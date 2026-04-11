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

import java.time.Duration;

/**
 * A grouped set of TTL durations that together describe the lifetime of one Odyssey stream.
 *
 * <p>Used as the per-category default on {@link
 * org.jwcarman.odyssey.autoconfigure.OdysseyProperties} (ephemeral / channel / broadcast) and as
 * the argument to {@link PublisherConfig#ttl(TtlPolicy)} when a caller wants to replace all three
 * TTLs on a publisher config in one operation instead of setting them individually.
 *
 * <p>The three fields map 1:1 to the three distinct Substrate lifecycle events:
 *
 * <ul>
 *   <li>{@code inactivityTtl} -- how long the journal lives without appends before auto-expiring.
 *       Set once when the journal is created in the backend. Reset on every successful append.
 *   <li>{@code entryTtl} -- applied on every {@code journal.append(data, ttl)} call. Each entry
 *       expires independently after this duration.
 *   <li>{@code retentionTtl} -- passed to {@code journal.complete(retentionTtl)} when the
 *       publisher's {@code complete()} method is called. After completion, no further appends are
 *       accepted but existing entries remain readable for this duration before the journal expires
 *       fully.
 * </ul>
 *
 * @param inactivityTtl the journal inactivity TTL
 * @param entryTtl the default per-entry TTL
 * @param retentionTtl the retention TTL used by {@link OdysseyPublisher#complete()}
 */
public record TtlPolicy(Duration inactivityTtl, Duration entryTtl, Duration retentionTtl) {

  /** Return a copy with a new {@code inactivityTtl}; the other two fields are preserved. */
  public TtlPolicy withInactivityTtl(Duration ttl) {
    return new TtlPolicy(ttl, entryTtl, retentionTtl);
  }

  /** Return a copy with a new {@code entryTtl}; the other two fields are preserved. */
  public TtlPolicy withEntryTtl(Duration ttl) {
    return new TtlPolicy(inactivityTtl, ttl, retentionTtl);
  }

  /** Return a copy with a new {@code retentionTtl}; the other two fields are preserved. */
  public TtlPolicy withRetentionTtl(Duration ttl) {
    return new TtlPolicy(inactivityTtl, entryTtl, ttl);
  }
}
