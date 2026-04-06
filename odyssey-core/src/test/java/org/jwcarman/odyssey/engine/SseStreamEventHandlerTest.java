package org.jwcarman.odyssey.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseStreamEventHandlerTest {

  private static OdysseyEvent testEvent(String id) {
    return OdysseyEvent.builder()
        .id(id)
        .streamKey("test-stream")
        .eventType("test")
        .payload("{\"n\":" + id + "}")
        .timestamp(Instant.now())
        .metadata(Map.of())
        .build();
  }

  @Test
  void onEventSendsToEmitter() throws Exception {
    SseEmitter emitter = spy(new SseEmitter(0L));
    Runnable cleanup = mock(Runnable.class);
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);

    OdysseyEvent event = testEvent("1-0");
    handler.onEvent(event);

    verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void onKeepAliveSendsCommentToEmitter() throws Exception {
    SseEmitter emitter = spy(new SseEmitter(0L));
    Runnable cleanup = mock(Runnable.class);
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);

    handler.onKeepAlive();

    verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void onCompleteCompletesEmitter() {
    SseEmitter emitter = spy(new SseEmitter(0L));
    Runnable cleanup = mock(Runnable.class);
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);

    handler.onComplete();

    verify(emitter).complete();
  }

  @Test
  void onErrorCompletesEmitterWithError() {
    SseEmitter emitter = spy(new SseEmitter(0L));
    Runnable cleanup = mock(Runnable.class);
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);

    Exception error = new RuntimeException("test error");
    handler.onError(error);

    verify(emitter).completeWithError(error);
  }

  @Test
  void onEventIOExceptionTriggersCleanup() throws Exception {
    SseEmitter emitter = spy(new SseEmitter(0L));
    doThrow(new IOException("connection reset"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));
    Runnable cleanup = mock(Runnable.class);
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);

    handler.onEvent(testEvent("1-0"));

    verify(cleanup).run();
  }

  @Test
  void onKeepAliveIOExceptionTriggersCleanup() throws Exception {
    SseEmitter emitter = spy(new SseEmitter(0L));
    doThrow(new IOException("connection reset"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));
    Runnable cleanup = mock(Runnable.class);
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);

    handler.onKeepAlive();

    verify(cleanup).run();
  }

  @Test
  void cleanupIsIdempotent() throws Exception {
    SseEmitter emitter = spy(new SseEmitter(0L));
    doThrow(new IOException("connection reset"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));
    AtomicInteger cleanupCount = new AtomicInteger(0);
    Runnable cleanup = cleanupCount::incrementAndGet;
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);

    // Trigger cleanup multiple times
    handler.onEvent(testEvent("1-0"));
    handler.onKeepAlive();

    assertEquals(1, cleanupCount.get(), "Cleanup should only run once");
  }

  @Test
  void emitterCallbacksRegistered() {
    SseEmitter emitter = spy(new SseEmitter(0L));
    Runnable cleanup = mock(Runnable.class);

    new SseStreamEventHandler(emitter, cleanup);

    verify(emitter).onCompletion(any());
    verify(emitter).onError(any());
    verify(emitter).onTimeout(any());
  }
}
