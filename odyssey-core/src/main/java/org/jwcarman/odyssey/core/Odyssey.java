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

import java.util.function.Consumer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Top-level facade for publishing events to and subscribing to Odyssey streams. Typical usage is to
 * inject {@code Odyssey} into a Spring {@code @RestController} and call it from request handlers.
 *
 * <h2>What Odyssey is</h2>
 *
 * <p>Odyssey is a library that makes {@code SseEmitter} handling ergonomic in Spring Boot. It turns
 * "I want to publish typed events to a named stream and have Spring's SSE emitter deliver them to
 * connected clients, with reconnect and resume support baked in" into a few-line affair. It is
 * deliberately unopinionated about how you name your streams or what lifetime policies you apply to
 * them -- those are application concerns.
 *
 * <h2>Streams have names, that's it</h2>
 *
 * <p>A stream is identified by a single string <strong>name</strong> that the caller provides.
 * Odyssey adds no prefixes, no categories, no namespacing. The name you pass to {@link
 * #publisher(String, Class)} is the name that lives in the backend journal, and the name you get
 * back from {@link OdysseyPublisher#name()} is exactly that same string.
 *
 * <p>If you want to namespace your streams (e.g., {@code "channel:user:alice"}, {@code
 * "broadcast:announcements"}, {@code "mcp-session:abc123"}), you do that yourself by picking names
 * with whatever convention fits your app. Odyssey doesn't care.
 *
 * <h2>TTL policies are values, not methods</h2>
 *
 * <p>Stream lifetime (inactivity TTL, entry TTL, retention TTL) is configured per-publisher via
 * {@link PublisherConfig}. The default {@link TtlPolicy} comes from {@link
 * org.jwcarman.odyssey.autoconfigure.OdysseyProperties#defaultTtl()}; pass a customizer to override
 * per call.
 *
 * <p>Applications that want several distinct TTL policies (e.g., short-lived task streams vs.
 * long-lived broadcast streams) should define their own {@link TtlPolicy} constants and pass them
 * via customizer:
 *
 * <pre>{@code
 * public final class TtlPolicies {
 *   public static final TtlPolicy SHORT_LIVED = new TtlPolicy(
 *       Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofMinutes(5));
 *   public static final TtlPolicy LONG_LIVED = new TtlPolicy(
 *       Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(24));
 * }
 *
 * // A short-lived task stream with a UUID name
 * var taskPub = odyssey.publisher(
 *     UUID.randomUUID().toString(),
 *     TaskProgress.class,
 *     cfg -> cfg.ttl(TtlPolicies.SHORT_LIVED));
 *
 * // A long-lived broadcast stream
 * var newsPub = odyssey.publisher(
 *     "announcements",
 *     Announcement.class,
 *     cfg -> cfg.ttl(TtlPolicies.LONG_LIVED));
 * }</pre>
 *
 * <p>For app-wide defaults (every publisher gets a specific policy unless a per-call customizer
 * overrides it), define a {@link PublisherCustomizer} Spring bean.
 *
 * <h2>Producer/consumer split</h2>
 *
 * <p>Odyssey splits stream access into two independent sides:
 *
 * <ul>
 *   <li>Producers call {@link #publisher(String, Class)} to get an {@link OdysseyPublisher}.
 *       Publishers own the journal lifecycle -- they create (or adopt) the journal eagerly at the
 *       call site.
 *   <li>Consumers call {@link #subscribe(String, Class)}, {@link #resume(String, Class, String)},
 *       or {@link #replay(String, Class, int)} to get an {@link SseEmitter} that is already driving
 *       a virtual-thread writer loop. The starting position is part of the method name -- the
 *       returned emitter is opaque from then on.
 * </ul>
 *
 * <p>Every method has a second overload that accepts a {@link Consumer} customizer. The customizer
 * mutates a {@link PublisherConfig} or {@link SubscriberConfig} directly; the library owns the
 * builder lifecycle. Application-wide defaults live in {@link PublisherCustomizer} and {@link
 * SubscriberCustomizer} Spring beans, which run before the per-call customizer so callers can still
 * override what they care about.
 *
 * <h2>Reattach / reconnect</h2>
 *
 * <p>Reattach is trivial: call {@link #publisher(String, Class)} with the same name. The first call
 * creates the journal; subsequent calls adopt it. If you want the reattached publisher to use the
 * same TTL policy as the original creator, pass the same customizer both times (or define a {@link
 * PublisherCustomizer} bean so the policy is applied automatically).
 */
public interface Odyssey {

  /**
   * Returns a publisher for the given stream name, seeded with the default TTL policy from {@link
   * org.jwcarman.odyssey.autoconfigure.OdysseyProperties#defaultTtl()}. Any {@link
   * PublisherCustomizer} Spring beans run after the default, so they can override TTLs app-wide.
   *
   * <p>If no journal exists at {@code name}, one is created with the configured inactivity TTL. If
   * a journal already exists (created by a previous call, possibly in a different process), this
   * method adopts it -- no collision error. Reattach is the same code path as creation.
   */
  <T> OdysseyPublisher<T> publisher(String name, Class<T> type);

  /**
   * Returns a publisher for the given stream name with a per-call customizer. The customizer runs
   * after the default TTL seed and any {@link PublisherCustomizer} beans, so it has the final say
   * on all config fields.
   */
  <T> OdysseyPublisher<T> publisher(
      String name, Class<T> type, Consumer<PublisherConfig> customizer);

  // ---- Subscriber side (consumer) ----
  // Starting position is in the method name, not the config.

  /** Live tail from the current head -- only new entries are delivered. */
  <T> SseEmitter subscribe(String name, Class<T> type);

  <T> SseEmitter subscribe(String name, Class<T> type, Consumer<SubscriberConfig<T>> customizer);

  /** Resume strictly after a known entry id, then continue tailing. */
  <T> SseEmitter resume(String name, Class<T> type, String lastEventId);

  <T> SseEmitter resume(
      String name, Class<T> type, String lastEventId, Consumer<SubscriberConfig<T>> customizer);

  /** Replay the last N retained entries, then continue tailing. */
  <T> SseEmitter replay(String name, Class<T> type, int count);

  <T> SseEmitter replay(
      String name, Class<T> type, int count, Consumer<SubscriberConfig<T>> customizer);
}
