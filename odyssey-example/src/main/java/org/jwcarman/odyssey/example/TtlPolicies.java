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
package org.jwcarman.odyssey.example;

import java.time.Duration;
import org.jwcarman.odyssey.core.TtlPolicy;

/**
 * Named {@link TtlPolicy} constants for the example app's three stream lifetimes. This is the
 * application-level "I know what shapes of streams I have" layer that Odyssey deliberately does not
 * provide -- each app picks its own TTL tiers and gives them meaningful names.
 */
final class TtlPolicies {

  /**
   * Short-lived request/response streams (per-task progress streams, MCP tool calls, etc.). 5
   * minutes covers reasonable client reconnect windows; beyond that, the task is presumed orphaned.
   */
  static final TtlPolicy EPHEMERAL =
      new TtlPolicy(Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofMinutes(5));

  /**
   * Per-user or per-entity notification streams. 1 hour of inactivity TTL so users who reconnect
   * within an hour pick up where they left off.
   */
  static final TtlPolicy CHANNEL =
      new TtlPolicy(Duration.ofHours(1), Duration.ofHours(1), Duration.ofHours(1));

  /** System-wide announcement streams. Events live for a day so late subscribers catch up. */
  static final TtlPolicy BROADCAST =
      new TtlPolicy(Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(24));

  private TtlPolicies() {}
}
