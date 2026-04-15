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
import java.util.UUID;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.SubscriberCustomizer;
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

  private static final String PROGRESS_EVENT = "progress";

  public record TaskProgress(int percent, String status) {}

  private final Streams streams;
  private final HostnameProvider hostname;

  public TaskController(Streams streams, HostnameProvider hostname) {
    this.streams = streams;
    this.hostname = hostname;
  }

  @PostMapping
  public Map<String, String> startTask() {
    String taskId = UUID.randomUUID().toString();
    OdysseyStream<TaskProgress> s = streams.taskProgress(taskId);

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                s.publish(PROGRESS_EVENT, new TaskProgress(0, "Starting..."));
                Thread.sleep(1500);
                s.publish(PROGRESS_EVENT, new TaskProgress(33, "Processing..."));
                Thread.sleep(1500);
                s.publish(PROGRESS_EVENT, new TaskProgress(66, "Almost done..."));
                Thread.sleep(1500);
                s.publish("complete", new TaskProgress(100, "Done!"));
              } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
              } finally {
                s.complete();
              }
            });

    return Map.of("streamName", taskId, "servedBy", hostname.get());
  }

  @GetMapping(value = "/{streamName}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @PathVariable String streamName,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    OdysseyStream<TaskProgress> s = streams.taskProgress(streamName);
    SubscriberCustomizer<TaskProgress> whoami = whoamiCustomizer();
    return lastEventId != null ? s.resume(lastEventId, whoami) : s.subscribe(whoami);
  }

  private SubscriberCustomizer<TaskProgress> whoamiCustomizer() {
    return cfg ->
        cfg.onSubscribe(
            emitter -> emitter.send(SseEmitter.event().name("whoami").data(hostname.get())));
  }
}
