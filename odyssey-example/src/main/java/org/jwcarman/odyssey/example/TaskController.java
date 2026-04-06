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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/task")
public class TaskController {

  private final OdysseyStreamRegistry registry;
  private final ConcurrentHashMap<String, OdysseyStream> activeStreams = new ConcurrentHashMap<>();

  public TaskController(OdysseyStreamRegistry registry) {
    this.registry = registry;
  }

  @PostMapping
  public Map<String, String> startTask() {
    OdysseyStream stream = registry.ephemeral();
    String streamKey = stream.getStreamKey();
    activeStreams.put(streamKey, stream);

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                stream.publishRaw("progress", "{\"percent\":0,\"status\":\"Starting...\"}");
                Thread.sleep(1500);
                stream.publishRaw("progress", "{\"percent\":33,\"status\":\"Processing...\"}");
                Thread.sleep(1500);
                stream.publishRaw("progress", "{\"percent\":66,\"status\":\"Almost done...\"}");
                Thread.sleep(1500);
                stream.publishRaw("complete", "{\"percent\":100,\"status\":\"Done!\"}");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                stream.close();
                activeStreams.remove(streamKey);
              }
            });

    return Map.of("streamKey", streamKey);
  }

  @GetMapping(value = "/{streamKey}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @PathVariable String streamKey,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    OdysseyStream stream = activeStreams.get(streamKey);
    if (stream == null) {
      throw new IllegalArgumentException("Unknown or expired task stream: " + streamKey);
    }
    if (lastEventId != null) {
      return stream.resumeAfter(lastEventId);
    }
    return stream.subscribe();
  }
}
