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

public interface OdysseyStream {

  SseEmitter subscribe();

  SseEmitter subscribe(Duration timeout);

  SseEmitter resumeAfter(String lastEventId);

  SseEmitter resumeAfter(String lastEventId, Duration timeout);

  SseEmitter replayLast(int count);

  SseEmitter replayLast(int count, Duration timeout);

  String publishRaw(String eventType, String payload);

  String publishJson(String eventType, Object payload);

  void close();

  void delete();

  String getStreamKey();
}
