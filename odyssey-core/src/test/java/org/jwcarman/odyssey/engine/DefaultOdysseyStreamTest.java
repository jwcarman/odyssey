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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.substrate.core.Journal;
import org.jwcarman.substrate.core.JournalCursor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DefaultOdysseyStreamTest {

  private static final String STREAM_KEY = "substrate:journal:channel:test";
  private static final long KEEP_ALIVE = 30_000;
  private static final long SSE_TIMEOUT = 0;

  @Mock private Journal<OdysseyEvent> journal;
  @Mock private JournalCursor<OdysseyEvent> cursor;

  private DefaultOdysseyStream stream;

  @BeforeEach
  void setUp() {
    lenient().when(journal.key()).thenReturn(STREAM_KEY);
    stream =
        new DefaultOdysseyStream(
            journal,
            new DefaultOdysseyStream.StreamConfig(
                KEEP_ALIVE, SSE_TIMEOUT, java.time.Duration.ofHours(1)),
            new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    stream.close();
  }

  @Test
  void publishCallsAppendOnJournal() {
    when(journal.append(any(OdysseyEvent.class), any())).thenReturn("1-0");

    String entryId = stream.publishRaw("test-event", "{\"data\":1}");

    assertEquals("1-0", entryId);
    verify(journal).append(any(OdysseyEvent.class), any());
  }

  @Test
  void subscribeReturnsEmitter() {
    when(journal.read()).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    var emitter = stream.subscribe();

    assertNotNull(emitter);
    verify(journal).read();
  }

  @Test
  void subscribeWithTimeoutReturnsEmitter() {
    when(journal.read()).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    var emitter = stream.subscribe(Duration.ofSeconds(60));

    assertNotNull(emitter);
  }

  @Test
  void resumeAfterCallsReadAfterOnJournal() {
    when(journal.readAfter("5-0")).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    stream.resumeAfter("5-0");

    verify(journal).readAfter("5-0");
  }

  @Test
  void resumeAfterReturnsEmitter() {
    when(journal.readAfter("5-0")).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    var emitter = stream.resumeAfter("5-0");

    assertNotNull(emitter);
  }

  @Test
  void resumeAfterWithTimeoutReturnsEmitter() {
    when(journal.readAfter("5-0")).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    var emitter = stream.resumeAfter("5-0", Duration.ofSeconds(60));

    assertNotNull(emitter);
  }

  @Test
  void replayLastCallsReadLastOnJournal() {
    when(journal.readLast(10)).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    stream.replayLast(10);

    verify(journal).readLast(10);
  }

  @Test
  void replayLastReturnsEmitter() {
    when(journal.readLast(5)).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    var emitter = stream.replayLast(5);

    assertNotNull(emitter);
  }

  @Test
  void replayLastWithTimeoutReturnsEmitter() {
    when(journal.readLast(5)).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    var emitter = stream.replayLast(5, Duration.ofSeconds(60));

    assertNotNull(emitter);
  }

  @Test
  void closeCompletesJournal() {
    stream.close();

    verify(journal).complete();
  }

  @Test
  void deleteDeletesJournal() {
    stream.delete();

    verify(journal).delete();
  }

  @Test
  void deleteClosesActiveSubscriptionsAndDeletesJournal() {
    when(journal.read()).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    stream.subscribe();

    stream.delete();

    verify(cursor).close();
    verify(journal).delete();
  }

  @Test
  void publishRawWithoutEventTypeCallsAppend() {
    when(journal.append(any(OdysseyEvent.class), any())).thenReturn("3-0");

    String entryId = stream.publishRaw("{\"data\":1}");

    assertEquals("3-0", entryId);
    verify(journal)
        .append(
            argThat(event -> event.eventType() == null && event.payload().equals("{\"data\":1}")),
            any());
  }

  @Test
  void publishJsonSerializesAndPublishes() {
    when(journal.append(any(OdysseyEvent.class), any())).thenReturn("2-0");

    String entryId = stream.publishJson("test-event", java.util.Map.of("key", "value"));

    assertEquals("2-0", entryId);
    verify(journal).append(any(OdysseyEvent.class), any());
  }

  @Test
  void publishJsonWithoutEventTypeSerializesAndPublishes() {
    when(journal.append(any(OdysseyEvent.class), any())).thenReturn("4-0");

    String entryId = stream.publishJson(java.util.Map.of("key", "value"));

    assertEquals("4-0", entryId);
    verify(journal)
        .append(argThat(event -> event.eventType() == null && event.payload() != null), any());
  }

  @Test
  void getStreamKeyReturnsJournalKey() {
    assertEquals(STREAM_KEY, stream.getStreamKey());
  }

  @Test
  void subscriberBuilderReturnsEmitter() {
    when(journal.read()).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    var emitter = stream.subscriber().subscribe();

    assertNotNull(emitter);
    verify(journal).read();
  }

  @Test
  void subscriberBuilderWithTimeoutReturnsEmitter() {
    when(journal.read()).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    var emitter = stream.subscriber().timeout(Duration.ofSeconds(60)).subscribe();

    assertNotNull(emitter);
  }

  @Test
  void subscriberBuilderWithMapperReturnsEmitter() {
    when(journal.read()).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    var emitter = stream.subscriber().mapper(SseEventMapper.DEFAULT).subscribe();

    assertNotNull(emitter);
  }

  @Test
  void subscriberBuilderResumeAfterReturnsEmitter() {
    when(journal.readAfter("5-0")).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    var emitter = stream.subscriber().resumeAfter("5-0");

    assertNotNull(emitter);
    verify(journal).readAfter("5-0");
  }

  @Test
  void subscriberBuilderReplayLastReturnsEmitter() {
    when(journal.readLast(10)).thenReturn(cursor);
    lenient().when(cursor.isOpen()).thenReturn(false);

    var emitter = stream.subscriber().replayLast(10);

    assertNotNull(emitter);
    verify(journal).readLast(10);
  }

  @Test
  void subscriberBuilderRejectsNullMapper() {
    assertThatThrownBy(() -> stream.subscriber().mapper(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("mapper");
  }

  @Test
  void subscriberBuilderRejectsNullTimeout() {
    assertThatThrownBy(() -> stream.subscriber().timeout(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("timeout");
  }
}
