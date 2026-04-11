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

/**
 * Typed producer for events of type {@code T}. A publisher owns one Odyssey stream -- it creates
 * the underlying journal (or adopts an existing one) when the {@link Odyssey} facade hands it back,
 * and finalizes it via {@link #complete()} or destroys it via {@link #delete()}.
 *
 * <p>Publishers are immutable after construction: the TTLs configured via {@link PublisherConfig}
 * are frozen at the call site. To change TTLs, construct a new publisher.
 *
 * <p>Publishers are intentionally <strong>not</strong> {@link AutoCloseable}. A publisher holds no
 * thread, lock, or socket resources of its own -- it's a typed handle over a Substrate journal.
 * Try-with-resources would suggest "close to release local resources," but the only effect of a
 * close would be to call {@code journal.complete(retentionTtl)}, which is a destructive business
 * decision that terminates the stream for every subscriber. That is the wrong default for
 * long-lived streams, where the publisher should stay open for the lifetime of the application.
 * Callers who genuinely want to finalize a stream must call {@link #complete()} explicitly.
 *
 * @param <T> the typed payload the publisher accepts on {@link #publish(Object)}
 */
public interface OdysseyPublisher<T> {

  /**
   * Append an entry to this stream with no SSE event type. The SSE {@code event:} field is omitted
   * on the wire, which is what MCP Streamable HTTP and other protocols that don't use named events
   * require.
   *
   * @param data the typed payload to append
   * @return the Substrate entry id (monotonically ordered within the stream; usable as an SSE
   *     {@code Last-Event-ID} for reconnect via {@link Odyssey#resume(String, Class, String)})
   */
  String publish(T data);

  /**
   * Append an entry with an SSE event type. The supplied {@code eventType} becomes the SSE {@code
   * event:} field on the wire, which vanilla SSE clients use to route to named handlers.
   *
   * @param eventType the SSE event name; non-null
   * @param data the typed payload to append
   * @return the Substrate entry id (monotonically ordered within the stream; usable as an SSE
   *     {@code Last-Event-ID} for reconnect via {@link Odyssey#resume(String, Class, String)})
   */
  String publish(String eventType, T data);

  /**
   * Finalize the underlying journal using the retention TTL configured on this publisher. After
   * this call, no further {@code publish} calls are accepted; existing entries remain readable for
   * the retention window, and subscribers drain cleanly then receive a {@link
   * SseEventMapper.TerminalState.Completed} terminal state.
   *
   * <p>This is a deliberate business decision, not a Java resource-management cleanup. Do not call
   * this in a finally block after a single publish on a long-lived stream -- you will terminate the
   * stream for every subscriber.
   */
  void complete();

  /**
   * Explicitly delete the journal. Active subscribers receive a {@link
   * SseEventMapper.TerminalState.Deleted} terminal state. Destructive -- use when you want entries
   * gone immediately, not after the retention window.
   */
  void delete();

  /**
   * The name that uniquely identifies this stream, returned verbatim as the caller supplied it to
   * {@link Odyssey#publisher(String, Class)}. Round-trip is guaranteed: passing {@code pub.name()}
   * back to any of the {@link Odyssey} publisher or subscriber methods resolves to the same
   * underlying journal.
   *
   * <p>This is a flat namespace -- Odyssey does not prefix the name with any category or namespace.
   * The only prefixing that happens is Substrate's own storage-layout prefix ({@code
   * substrate:journal:...}), which is an implementation detail of the backend and not something
   * callers ever see or need to think about.
   *
   * @return the caller-supplied stream name
   */
  String name();
}
