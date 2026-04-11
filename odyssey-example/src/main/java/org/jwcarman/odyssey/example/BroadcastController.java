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

  private static final String STREAM_NAME = "announcements";

  record Announcement(String message) {}

  private final Odyssey odyssey;

  public BroadcastController(Odyssey odyssey) {
    this.odyssey = odyssey;
  }

  @PostMapping
  public Map<String, String> publish(@RequestBody Map<String, String> body) {
    // Long-lived broadcast stream. No try-with-resources: closing a long-lived stream
    // would terminate it for every subscriber. We apply our "BROADCAST" TTL policy via
    // the per-call customizer; Odyssey doesn't have opinions about what broadcast means.
    var pub =
        odyssey.publisher(STREAM_NAME, Announcement.class, cfg -> cfg.ttl(TtlPolicies.BROADCAST));
    String id = pub.publish("message", new Announcement(body.get("message")));
    return Map.of("id", id);
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    if (lastEventId != null) {
      return odyssey.resume(STREAM_NAME, Announcement.class, lastEventId);
    }
    return odyssey.subscribe(STREAM_NAME, Announcement.class);
  }
}
