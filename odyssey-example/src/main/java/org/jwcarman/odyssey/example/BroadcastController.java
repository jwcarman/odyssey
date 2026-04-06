package org.jwcarman.odyssey.example;

import java.util.Map;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
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

  private final OdysseyStreamRegistry registry;

  public BroadcastController(OdysseyStreamRegistry registry) {
    this.registry = registry;
  }

  @PostMapping
  public Map<String, String> publish(@RequestBody Map<String, String> body) {
    OdysseyStream stream = registry.broadcast("announcements");
    String id = stream.publish("message", body.get("message"));
    return Map.of("id", id);
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    OdysseyStream stream = registry.broadcast("announcements");
    if (lastEventId != null) {
      return stream.resumeAfter(lastEventId);
    }
    return stream.subscribe();
  }
}
