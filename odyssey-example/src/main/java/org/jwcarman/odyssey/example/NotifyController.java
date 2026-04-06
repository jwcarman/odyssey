package org.jwcarman.odyssey.example;

import java.util.Map;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notify")
public class NotifyController {

  private final OdysseyStreamRegistry registry;

  public NotifyController(OdysseyStreamRegistry registry) {
    this.registry = registry;
  }

  @PostMapping("/{userId}")
  public Map<String, String> publish(
      @PathVariable String userId, @RequestBody Map<String, String> body) {
    OdysseyStream stream = registry.channel("user:" + userId);
    String id = stream.publish("notification", body.get("message"));
    return Map.of("id", id);
  }

  @GetMapping(value = "/{userId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @PathVariable String userId,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    OdysseyStream stream = registry.channel("user:" + userId);
    if (lastEventId != null) {
      return stream.resumeAfter(lastEventId);
    }
    return stream.subscribe();
  }
}
