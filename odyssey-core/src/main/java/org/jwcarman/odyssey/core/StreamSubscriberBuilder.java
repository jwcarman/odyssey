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
 * Fluent builder for configuring and creating stream subscriptions. Obtained via {@link
 * OdysseyStream#subscriber()}.
 *
 * <p>Configuration methods return the builder for chaining. Terminal methods ({@link #subscribe()},
 * {@link #resumeAfter(String)}, {@link #replayLast(int)}) create the subscription and return an
 * {@link SseEmitter}.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * SseEmitter emitter = stream.subscriber()
 *     .timeout(Duration.ofMinutes(5))
 *     .mapper(myCustomMapper)
 *     .subscribe();
 * }</pre>
 */
public interface StreamSubscriberBuilder {

  /**
   * Sets the SSE connection timeout.
   *
   * @param timeout the timeout duration, or {@link Duration#ZERO} for no timeout
   * @return this builder
   */
  StreamSubscriberBuilder timeout(Duration timeout);

  /**
   * Sets a custom SSE event mapper that controls how {@link OdysseyEvent}s are written to the SSE
   * output.
   *
   * @param mapper the event mapper
   * @return this builder
   */
  StreamSubscriberBuilder mapper(SseEventMapper mapper);

  /**
   * Terminal operation: subscribes to live events. No historical events are delivered.
   *
   * @return an {@link SseEmitter} that will emit events as they are published
   */
  SseEmitter subscribe();

  /**
   * Terminal operation: resumes from a known event ID. Missed events are replayed first, then live
   * delivery continues.
   *
   * @param lastEventId the last event ID the client received
   * @return an {@link SseEmitter} that replays missed events then continues live
   */
  SseEmitter resumeAfter(String lastEventId);

  /**
   * Terminal operation: replays the last N events, then continues with live delivery.
   *
   * @param count the number of recent events to replay
   * @return an {@link SseEmitter} that delivers historical events followed by live events
   */
  SseEmitter replayLast(int count);
}
