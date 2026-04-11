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
 * Mutable configuration handed to a publisher customizer lambda. Setters return {@code this} for
 * chaining inside the lambda, but callers must not hold a reference to a {@code PublisherConfig}
 * outside the customizer -- the library freezes the config into a publisher as soon as the
 * customizer returns.
 *
 * <p>A fresh config is created on every call to {@link Odyssey#publisher(String, Class)} or {@link
 * Odyssey#publisher(String, Class, PublisherCustomizer)}, seeded from the default {@link TtlPolicy}
 * on {@link org.jwcarman.odyssey.autoconfigure.OdysseyProperties#defaultTtl()}. The caller's
 * per-call customizer (if any) runs after the seed and has the final say on every field.
 *
 * <p>The three TTL knobs correspond to three distinct Substrate lifecycle events:
 *
 * <ul>
 *   <li>{@link #inactivityTtl(Duration)} -- how long the journal lives without appends before
 *       auto-expiring. <strong>Creation-time only</strong>: if the publisher falls back to {@code
 *       connect()} because the journal already exists (cluster race), this setting is ignored --
 *       the journal keeps whatever inactivity TTL its original creator set.
 *   <li>{@link #entryTtl(Duration)} -- the default per-entry TTL applied on every {@code
 *       publish(...)}. Client-side; different publishers against the same journal can use different
 *       entry TTLs.
 *   <li>{@link #retentionTtl(Duration)} -- the retention passed to {@code journal.complete(...)}
 *       when {@link OdysseyPublisher#complete()} is called.
 * </ul>
 *
 * <p>The {@link #ttl(TtlPolicy)} default method is a convenience for replacing all three fields
 * from a {@link TtlPolicy} record in one call; use it when you have a named policy constant you
 * want to apply wholesale, and use the individual setters when you only need to tweak one field.
 */
public interface PublisherConfig {

  /**
   * Replace all three TTL fields from a {@link TtlPolicy} record in a single call. Equivalent to
   * calling {@link #inactivityTtl(Duration)}, {@link #entryTtl(Duration)}, and {@link
   * #retentionTtl(Duration)} with the three fields from {@code ttl}.
   *
   * @param ttl the policy to apply; all three fields are required (no {@code null} handling)
   * @return this config, for chaining
   */
  default PublisherConfig ttl(TtlPolicy ttl) {
    return inactivityTtl(ttl.inactivityTtl())
        .entryTtl(ttl.entryTtl())
        .retentionTtl(ttl.retentionTtl());
  }

  /**
   * Set the journal's inactivity TTL -- how long the journal lives without new appends before
   * Substrate expires it. See the class-level docs for the creation-time caveat: this setting is
   * only honored when the publisher is the journal's original creator.
   *
   * @param ttl the inactivity duration
   * @return this config, for chaining
   */
  PublisherConfig inactivityTtl(Duration ttl);

  /**
   * Set the default per-entry TTL applied on every {@code publish(...)} call on the resulting
   * publisher. Each published entry expires independently after this duration.
   *
   * @param ttl the per-entry duration
   * @return this config, for chaining
   */
  PublisherConfig entryTtl(Duration ttl);

  /**
   * Set the retention TTL used by {@link OdysseyPublisher#complete()}. After completion, no further
   * appends are accepted but existing entries remain readable for this duration so late-joining
   * subscribers can drain the stream before it closes.
   *
   * @param ttl the post-completion retention duration
   * @return this config, for chaining
   */
  PublisherConfig retentionTtl(Duration ttl);
}
