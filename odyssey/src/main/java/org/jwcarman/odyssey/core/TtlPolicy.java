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
 * <p>Used as the type of {@link org.jwcarman.odyssey.autoconfigure.OdysseyProperties#defaultTtl()}
 * (the default TTL policy applied when {@link Odyssey#stream(String, Class)} creates a new stream)
 * and as the third argument to {@link Odyssey#stream(String, Class, TtlPolicy)} when the caller
 * wants to apply a specific policy. Applications that want several TTL tiers (e.g., short-lived
 * task streams vs. long-lived broadcast streams) should define their own {@code TtlPolicy}
 * constants and pass them to {@code stream(...)}.
 *
 * <p>The three fields map 1:1 to three distinct Substrate lifecycle events:
 *
 * <ul>
 *   <li>{@code inactivityTtl} -- how long the journal lives without appends before auto-expiring.
 *       Set once when the journal is created in the backend. Reset on every successful append.
 *   <li>{@code entryTtl} -- applied on every {@code journal.append(data, ttl)} call. Each entry
 *       expires independently after this duration.
 *   <li>{@code retentionTtl} -- passed to {@code journal.complete(retentionTtl)} when the
 *       publisher's {@link OdysseyStream#complete()} method is called. After completion, no further
 *       appends are accepted but existing entries remain readable for this duration before the
 *       journal expires fully.
 * </ul>
 *
 * <p>{@code TtlPolicy} is a value type and is safe to hold in {@code static final} constants. The
 * three copy-with helpers ({@link #withInactivityTtl(Duration)}, {@link #withEntryTtl(Duration)},
 * {@link #withRetentionTtl(Duration)}) let callers derive variants from a base policy without
 * mutating it.
 *
 * @param inactivityTtl the journal inactivity TTL
 * @param entryTtl the default per-entry TTL
 * @param retentionTtl the retention TTL used by {@link OdysseyStream#complete()}
 */
public record TtlPolicy(Duration inactivityTtl, Duration entryTtl, Duration retentionTtl) {

  /**
   * Returns a copy of this policy with a new {@code inactivityTtl}; the other two fields are
   * preserved.
   *
   * @param ttl the replacement inactivity TTL
   * @return a new {@code TtlPolicy} with the updated inactivity TTL
   */
  public TtlPolicy withInactivityTtl(Duration ttl) {
    return new TtlPolicy(ttl, entryTtl, retentionTtl);
  }

  /**
   * Returns a copy of this policy with a new {@code entryTtl}; the other two fields are preserved.
   *
   * @param ttl the replacement per-entry TTL
   * @return a new {@code TtlPolicy} with the updated entry TTL
   */
  public TtlPolicy withEntryTtl(Duration ttl) {
    return new TtlPolicy(inactivityTtl, ttl, retentionTtl);
  }

  /**
   * Returns a copy of this policy with a new {@code retentionTtl}; the other two fields are
   * preserved.
   *
   * @param ttl the replacement retention TTL
   * @return a new {@code TtlPolicy} with the updated retention TTL
   */
  public TtlPolicy withRetentionTtl(Duration ttl) {
    return new TtlPolicy(inactivityTtl, entryTtl, ttl);
  }
}
