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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.substrate.core.JournalCursor;
import org.jwcarman.substrate.core.JournalEntry;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class StreamSubscriptionTest {

  private static final long KEEP_ALIVE_INTERVAL = 100;
  private static final String STREAM_KEY = "test-stream";

  private JournalCursor<OdysseyEvent> cursor;
  private SseEmitter emitter;
  private CopyOnWriteArrayList<StreamSubscription> subscriptionList;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    cursor = mock(JournalCursor.class);
    emitter = mock(SseEmitter.class);
    subscriptionList = new CopyOnWriteArrayList<>();
  }

  private StreamSubscription createSubscription() {
    StreamSubscription sub =
        new StreamSubscription(cursor, emitter, STREAM_KEY, KEEP_ALIVE_INTERVAL, subscriptionList);
    subscriptionList.add(sub);
    return sub;
  }

  private static OdysseyEvent testEvent(String id, String eventType, String payload) {
    return OdysseyEvent.builder()
        .id(id)
        .streamKey(STREAM_KEY)
        .eventType(eventType)
        .payload(payload)
        .timestamp(Instant.now())
        .metadata(Map.of())
        .build();
  }

  private static JournalEntry<OdysseyEvent> journalEntry(String id, OdysseyEvent event) {
    return new JournalEntry<>(id, STREAM_KEY, event, Instant.now());
  }

  @Test
  void startSendsConnectedComment() throws IOException {
    when(cursor.isOpen()).thenReturn(false);
    StreamSubscription sub = createSubscription();
    sub.start();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> verify(emitter).send(any(SseEmitter.SseEventBuilder.class)));
  }

  @Test
  void writerLoopSendsEventsFromCursor() throws IOException {
    OdysseyEvent event = testEvent("1-0", "msg", "hello");
    when(cursor.isOpen()).thenReturn(true, true, false);
    when(cursor.poll(any())).thenReturn(Optional.of(journalEntry("1-0", event)), Optional.empty());

    StreamSubscription sub = createSubscription();
    sub.start();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> verify(emitter, atLeast(2)).send(any(SseEmitter.SseEventBuilder.class)));
  }

  @Test
  void writerLoopSendsKeepAliveOnTimeout() throws IOException {
    when(cursor.isOpen()).thenReturn(true, true, false);
    when(cursor.poll(any())).thenReturn(Optional.empty());

    StreamSubscription sub = createSubscription();
    sub.start();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> verify(emitter, atLeast(2)).send(any(SseEmitter.SseEventBuilder.class)));
  }

  @Test
  void writerLoopCompletesEmitterWhenCursorCloses() {
    when(cursor.isOpen()).thenReturn(false);

    StreamSubscription sub = createSubscription();
    sub.start();

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(emitter).complete());
  }

  @Test
  void sendFailureClosesSubscription() throws IOException {
    when(cursor.isOpen()).thenReturn(true, false);
    when(cursor.poll(any())).thenReturn(Optional.empty());
    doThrow(new IOException("broken pipe"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    StreamSubscription sub = createSubscription();
    sub.start();

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(cursor).close());
    assertThat(subscriptionList).isEmpty();
  }

  @Test
  void closeIsIdempotent() {
    StreamSubscription sub = createSubscription();
    sub.close();
    sub.close();
    verify(cursor, times(1)).close();
    assertThat(subscriptionList).isEmpty();
  }

  @Test
  void closeRemovesFromSubscriptionList() {
    StreamSubscription sub = createSubscription();
    assertThat(subscriptionList).hasSize(1);
    sub.close();
    assertThat(subscriptionList).isEmpty();
  }

  @Test
  void eventWithNullEventTypeSkipsName() throws IOException {
    OdysseyEvent event = testEvent("1-0", null, "data");
    when(cursor.isOpen()).thenReturn(true, false);
    when(cursor.poll(any())).thenReturn(Optional.of(journalEntry("1-0", event)));

    StreamSubscription sub = createSubscription();
    sub.start();

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(emitter).complete());
  }

  @Test
  void writerLoopHandlesUnexpectedException() {
    when(cursor.isOpen()).thenReturn(true);
    when(cursor.poll(any())).thenThrow(new RuntimeException("unexpected"));

    StreamSubscription sub = createSubscription();
    sub.start();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> verify(emitter).completeWithError(any(RuntimeException.class)));
  }

  @Test
  void emitterCallbacksClose() throws IOException {
    when(cursor.isOpen()).thenReturn(false);

    StreamSubscription sub = createSubscription();
    sub.start();

    // Capture the callbacks
    var completionCaptor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
    verify(emitter).onCompletion(completionCaptor.capture());
    completionCaptor.getValue().run();

    verify(cursor).close();
    assertThat(subscriptionList).isEmpty();
  }
}
