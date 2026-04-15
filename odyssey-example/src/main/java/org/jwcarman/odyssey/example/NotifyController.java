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
import org.jwcarman.odyssey.core.SubscriberCustomizer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notify")
public class NotifyController {

  public record Notification(String message) {}

  private final Streams streams;
  private final HostnameProvider hostname;

  public NotifyController(Streams streams, HostnameProvider hostname) {
    this.streams = streams;
    this.hostname = hostname;
  }

  @PostMapping("/{userId}")
  public Map<String, String> publish(
      @PathVariable String userId, @RequestBody Map<String, String> body) {
    String id =
        streams.userChannel(userId).publish("notification", new Notification(body.get("message")));
    return Map.of("id", id, "servedBy", hostname.get());
  }

  @GetMapping(value = "/{userId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @PathVariable String userId,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
      @RequestParam(value = "lastEventId", required = false) String lastEventIdParam) {
    String lastEventId = lastEventIdHeader != null ? lastEventIdHeader : lastEventIdParam;
    var s = streams.userChannel(userId);
    SubscriberCustomizer<Notification> whoami = whoamiCustomizer();
    return lastEventId != null ? s.resume(lastEventId, whoami) : s.subscribe(whoami);
  }

  private SubscriberCustomizer<Notification> whoamiCustomizer() {
    return cfg ->
        cfg.onSubscribe(
            emitter -> emitter.send(SseEmitter.event().name("whoami").data(hostname.get())));
  }
}
