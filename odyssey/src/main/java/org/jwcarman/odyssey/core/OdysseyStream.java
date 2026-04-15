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

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Typed handle to a single Odyssey stream. Obtained from {@link Odyssey#stream(String, Class,
 * TtlPolicy)} (or its default-TTL overload). Carries the stream's name, element type, and TTL
 * policy; every publish, subscribe, resume, and replay call against this stream is a method on this
 * handle rather than a call on the {@link Odyssey} facade.
 *
 * <h2>Broker semantics</h2>
 *
 * <p>Streams are implicitly created the first time any caller asks for one. TTL is fixed at the
 * first such call for a given name; later callers' TTL argument is ignored and they adopt whatever
 * policy the stream already has. This matches Kafka, ActiveMQ, and NATS JetStream conventions --
 * producers and consumers do not configure retention on every operation.
 *
 * <p>Type is caller-asserted. Odyssey does not persist or enforce type identity; using the same
 * name with different types across callers produces deserialization errors at publish or subscribe
 * time, not declaration-time errors.
 *
 * <h2>Handles are not cached</h2>
 *
 * <p>Each call to {@link Odyssey#stream(String, Class, TtlPolicy)} returns a fresh handle. Callers
 * that want to cache a handle (e.g., a {@code @Component} that holds {@code
 * OdysseyStream<Announcement>} for the life of the app) should do so at the application layer.
 *
 * @param <T> the typed payload of this stream
 */
public interface OdysseyStream<T> {

  /**
   * Returns the caller-supplied stream name verbatim. Round-trip is guaranteed: passing {@code
   * stream.name()} back to {@link Odyssey#stream(String, Class, TtlPolicy)} resolves to the same
   * underlying stream.
   *
   * @return the stream name
   */
  String name();

  /**
   * Append an entry with no SSE event type. The SSE {@code event:} field is omitted on the wire,
   * which is what MCP Streamable HTTP and other protocols that don't use named events require.
   *
   * @param data the typed payload to append
   * @return the Substrate entry id (monotonically ordered; usable as an SSE {@code Last-Event-ID}
   *     for reconnect via {@link #resume(String)})
   */
  String publish(T data);

  /**
   * Append an entry with an SSE event type. The supplied {@code eventType} becomes the SSE {@code
   * event:} field on the wire.
   *
   * @param eventType the SSE event name; non-null
   * @param data the typed payload to append
   * @return the Substrate entry id
   */
  String publish(String eventType, T data);

  /**
   * Subscribe to this stream, delivering only entries appended after this call.
   *
   * @return an {@code SseEmitter} already driving a writer loop for this subscription
   */
  SseEmitter subscribe();

  /**
   * Subscribe with a per-call customizer that mutates the {@link SubscriberConfig} before the
   * writer loop starts.
   *
   * @param customizer mutates the subscriber config
   * @return an {@code SseEmitter} already driving a writer loop for this subscription
   */
  SseEmitter subscribe(SubscriberCustomizer<T> customizer);

  /**
   * Resume strictly after a known entry id, then continue tailing. The first value delivered is the
   * entry immediately following {@code lastEventId}, not {@code lastEventId} itself.
   *
   * @param lastEventId the entry id to resume after; typically an SSE {@code Last-Event-ID} header
   *     value
   * @return an {@code SseEmitter} already driving a writer loop for this subscription
   */
  SseEmitter resume(String lastEventId);

  /**
   * Resume strictly after a known entry id, with a per-call customizer.
   *
   * @param lastEventId the entry id to resume after
   * @param customizer mutates the subscriber config
   * @return an {@code SseEmitter} already driving a writer loop for this subscription
   */
  SseEmitter resume(String lastEventId, SubscriberCustomizer<T> customizer);

  /**
   * Replay the last {@code count} retained entries, then continue tailing.
   *
   * @param count how many recent entries to replay before tailing
   * @return an {@code SseEmitter} already driving a writer loop for this subscription
   */
  SseEmitter replay(int count);

  /**
   * Replay the last {@code count} retained entries then tail, with a per-call customizer.
   *
   * @param count how many recent entries to replay before tailing
   * @param customizer mutates the subscriber config
   * @return an {@code SseEmitter} already driving a writer loop for this subscription
   */
  SseEmitter replay(int count, SubscriberCustomizer<T> customizer);

  /**
   * Finalize the underlying journal using the stream's retention TTL. After this call, no further
   * {@link #publish} calls are accepted; existing entries remain readable for the retention window
   * and subscribers eventually receive a {@link SseEventMapper.TerminalState.Completed} terminal
   * state.
   *
   * <p>Destructive across all current subscribers -- do not call this to "release" a handle. It is
   * a business decision to close the stream.
   */
  void complete();

  /**
   * Explicitly delete the stream. Active subscribers receive a {@link
   * SseEventMapper.TerminalState.Deleted} terminal state. Use when you want entries gone
   * immediately rather than after the retention window.
   */
  void delete();
}
