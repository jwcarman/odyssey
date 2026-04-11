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
import org.jwcarman.odyssey.core.Odyssey;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/broadcast")
public class BroadcastController {

  record Announcement(String message) {}

  private final Odyssey odyssey;

  public BroadcastController(Odyssey odyssey) {
    this.odyssey = odyssey;
  }

  @PostMapping
  public Map<String, String> publish(@RequestBody Map<String, String> body) {
    String key = "broadcast:announcements";
    try (var pub = odyssey.broadcast("announcements", Announcement.class)) {
      String id = pub.publish("message", new Announcement(body.get("message")));
      return Map.of("id", id);
    }
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    String key = "broadcast:announcements";
    if (lastEventId != null) {
      return odyssey.resume(key, Announcement.class, lastEventId);
    }
    return odyssey.subscribe(key, Announcement.class);
  }
}
