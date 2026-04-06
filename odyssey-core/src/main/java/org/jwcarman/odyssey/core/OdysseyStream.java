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

  String publish(String eventType, String payload);

  void close();

  void delete();

  String getStreamKey();
}
