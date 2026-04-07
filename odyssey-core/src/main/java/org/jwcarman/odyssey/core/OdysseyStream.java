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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * A single Server-Sent Events stream that supports publishing, subscribing, replay, and lifecycle
 * management. Streams are obtained from an {@link OdysseyStreamRegistry} and come in three flavors:
 * ephemeral, channel, and broadcast.
 *
 * <p>Subscribers receive events via an {@link SseEmitter}. Each subscriber is backed by two virtual
 * threads (reader and writer) coordinated through a blocking queue, ensuring non-blocking I/O and
 * automatic keep-alive delivery.
 *
 * <p>All subscribe methods start delivering events from the moment the subscription is created. To
 * receive events published before the subscription, use {@link #resumeAfter(String)} or {@link
 * #replayLast(int)}.
 *
 * @see OdysseyStreamRegistry
 * @see OdysseyEvent
 */
public interface OdysseyStream {

  /**
   * Subscribes to live events from this stream using the registry's default SSE timeout. No
   * historical events are delivered; only events published after this call will be received.
   *
   * <p>The returned emitter's lifecycle is managed by OdySSEy. Callers should not call {@link
   * SseEmitter#complete()} directly; use {@link #close()} or {@link #delete()} instead.
   *
   * @return an {@link SseEmitter} that will emit events as they are published to this stream
   */
  SseEmitter subscribe();

  /**
   * Subscribes to live events from this stream with a custom SSE timeout. No historical events are
   * delivered; only events published after this call will be received.
   *
   * @param timeout the SSE connection timeout, or {@link Duration#ZERO} for no timeout
   * @return an {@link SseEmitter} that will emit events as they are published to this stream
   */
  SseEmitter subscribe(Duration timeout);

  /**
   * Resumes a subscription after a known event ID. All events published strictly after {@code
   * lastEventId} are replayed first, then the emitter transitions to live delivery. This supports
   * SSE reconnect semantics where the client provides the {@code Last-Event-ID} header.
   *
   * <p>If {@code lastEventId} is no longer present in the event log (e.g., it has been trimmed),
   * the replay begins from the earliest available event.
   *
   * @param lastEventId the ID of the last event the client successfully received
   * @return an {@link SseEmitter} that replays missed events, then continues with live events
   */
  SseEmitter resumeAfter(String lastEventId);

  /**
   * Resumes a subscription after a known event ID with a custom SSE timeout.
   *
   * @param lastEventId the ID of the last event the client successfully received
   * @param timeout the SSE connection timeout, or {@link Duration#ZERO} for no timeout
   * @return an {@link SseEmitter} that replays missed events, then continues with live events
   * @see #resumeAfter(String)
   */
  SseEmitter resumeAfter(String lastEventId, Duration timeout);

  /**
   * Replays the last {@code count} events from this stream in chronological order, then transitions
   * to live delivery. The count is capped at the configured {@code maxLastN} value (default 500).
   *
   * @param count the number of recent events to replay (capped by the configured maximum)
   * @return an {@link SseEmitter} that delivers historical events followed by live events
   */
  SseEmitter replayLast(int count);

  /**
   * Replays the last {@code count} events from this stream with a custom SSE timeout.
   *
   * @param count the number of recent events to replay (capped by the configured maximum)
   * @param timeout the SSE connection timeout, or {@link Duration#ZERO} for no timeout
   * @return an {@link SseEmitter} that delivers historical events followed by live events
   * @see #replayLast(int)
   */
  SseEmitter replayLast(int count, Duration timeout);

  /**
   * Publishes a raw string payload to this stream. The payload is stored as-is with no
   * serialization.
   *
   * @param eventType the SSE event type (sent as the {@code event:} field), or {@code null} for the
   *     default event type
   * @param payload the event data (sent as the {@code data:} field)
   * @return the generated event ID, suitable for use with {@link #resumeAfter(String)}
   */
  String publishRaw(String eventType, String payload);

  /**
   * Publishes an object to this stream, serializing it to JSON using the configured {@link
   * tools.jackson.databind.ObjectMapper}.
   *
   * @param eventType the SSE event type (sent as the {@code event:} field), or {@code null} for the
   *     default event type
   * @param payload the object to serialize as JSON for the event data
   * @return the generated event ID, suitable for use with {@link #resumeAfter(String)}
   */
  String publishJson(String eventType, Object payload);

  /**
   * Gracefully closes this stream. Existing subscribers receive all remaining queued events before
   * being disconnected. No new events can be published after this call.
   *
   * <p>Use this for orderly shutdown when all data has been published (e.g., an MCP tool call
   * completing).
   *
   * @see #delete()
   */
  void close();

  /**
   * Immediately deletes this stream and its event history. All subscribers are disconnected without
   * draining queued events, and the underlying storage is removed.
   *
   * <p>Use this when the stream is no longer needed and prompt cleanup is more important than
   * delivering remaining events (e.g., client disconnect or error recovery).
   *
   * @see #close()
   */
  void delete();

  /**
   * Returns the unique key identifying this stream. The key format depends on the stream type
   * (e.g., {@code odyssey:ephemeral:<uuid>}, {@code odyssey:channel:<name>}). Clients should treat
   * stream keys as opaque strings suitable for use with {@link
   * OdysseyStreamRegistry#stream(String)} to reconnect.
   *
   * @return the stream key
   */
  String getStreamKey();
}
