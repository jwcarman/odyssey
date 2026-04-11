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
 * <p>Three TTL knobs correspond to three distinct Substrate lifecycle events:
 *
 * <ul>
 *   <li>{@link #inactivityTtl(Duration)} -- how long the journal lives without appends before
 *       auto-expiring. <strong>Creation-time only</strong>: if the publisher falls back to {@code
 *       connect()} because the journal already exists (cluster race), this setting is ignored --
 *       the journal keeps whatever inactivity TTL its original creator set.
 *   <li>{@link #entryTtl(Duration)} -- the default per-entry TTL applied on every {@code
 *       publish(...)}. Client-side; different publishers against the same journal can use different
 *       entry TTLs.
 *   <li>{@link #retentionTtl(Duration)} -- the default retention passed to {@code
 *       journal.complete(...)} when {@link OdysseyPublisher#close()} is called without an explicit
 *       value. Client-side; can be overridden per-call via {@link
 *       OdysseyPublisher#close(Duration)}.
 * </ul>
 */
public interface PublisherConfig {

  /** Set the journal's inactivity TTL. See class-level docs for the creation-time caveat. */
  PublisherConfig inactivityTtl(Duration ttl);

  /** Set the default per-entry TTL applied on every {@code publish(...)}. */
  PublisherConfig entryTtl(Duration ttl);

  /** Set the default retention TTL used by {@link OdysseyPublisher#close()}. */
  PublisherConfig retentionTtl(Duration ttl);
}
