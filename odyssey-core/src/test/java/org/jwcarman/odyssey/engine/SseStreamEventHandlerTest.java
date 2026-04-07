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
import org.mockito.ArgumentCaptor;
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

  private static OdysseyEvent testEventWithNullType(String id) {
    return OdysseyEvent.builder()
        .id(id)
        .streamKey("test-stream")
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
  void onEventWithNullEventTypeSendsToEmitter() throws Exception {
    SseEmitter emitter = spy(new SseEmitter(0L));
    Runnable cleanup = mock(Runnable.class);
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);

    OdysseyEvent event = testEventWithNullType("1-0");
    handler.onEvent(event);

    verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void onEventWithNullEventTypeOmitsNameField() throws Exception {
    SseEmitter emitter = spy(new SseEmitter(0L));
    Runnable cleanup = mock(Runnable.class);
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);

    OdysseyEvent event = testEventWithNullType("1-0");
    handler.onEvent(event);

    var captor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
    verify(emitter).send(captor.capture());
    String sseOutput =
        captor.getValue().build().stream()
            .map(d -> d.getData().toString())
            .collect(java.util.stream.Collectors.joining());
    assertFalse(sseOutput.contains("event:"), "SSE output should not contain event: field");
    assertTrue(sseOutput.contains("id:1-0"), "SSE output should contain id field");
    assertTrue(sseOutput.contains("data:"), "SSE output should contain data field");
  }

  @Test
  void onEventWithEventTypeIncludesNameField() throws Exception {
    SseEmitter emitter = spy(new SseEmitter(0L));
    Runnable cleanup = mock(Runnable.class);
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);

    OdysseyEvent event = testEvent("1-0");
    handler.onEvent(event);

    var captor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
    verify(emitter).send(captor.capture());
    String sseOutput =
        captor.getValue().build().stream()
            .map(d -> d.getData().toString())
            .collect(java.util.stream.Collectors.joining());
    assertTrue(sseOutput.contains("event:test"), "SSE output should contain event: field");
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
