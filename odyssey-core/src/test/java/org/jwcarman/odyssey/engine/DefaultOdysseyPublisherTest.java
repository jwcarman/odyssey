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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyPublisher;
import org.jwcarman.substrate.journal.Journal;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DefaultOdysseyPublisherTest {

  private static final Duration ENTRY_TTL = Duration.ofHours(1);
  private static final Duration RETENTION_TTL = Duration.ofMinutes(5);

  @Mock private Journal<StoredEvent> journal;

  private OdysseyPublisher<TestPayload> publisher;
  private final ObjectMapper objectMapper = new ObjectMapper();

  record TestPayload(String name, int value) {}

  @BeforeEach
  void setUp() {
    when(journal.key()).thenReturn("test-key");
    publisher = new DefaultOdysseyPublisher<>(journal, objectMapper, ENTRY_TTL, RETENTION_TTL);
  }

  @Test
  void publishWithEventType() {
    when(journal.append(any(), eq(ENTRY_TTL))).thenReturn("id-1");

    String id = publisher.publish("event.type", new TestPayload("hello", 42));

    assertThat(id).isEqualTo("id-1");
    ArgumentCaptor<StoredEvent> captor = ArgumentCaptor.forClass(StoredEvent.class);
    verify(journal).append(captor.capture(), eq(ENTRY_TTL));
    StoredEvent stored = captor.getValue();
    assertThat(stored.eventType()).isEqualTo("event.type");
    assertThat(stored.data()).contains("hello");
    assertThat(stored.data()).contains("42");
  }

  @Test
  void publishWithoutEventType() {
    when(journal.append(any(), eq(ENTRY_TTL))).thenReturn("id-2");

    String id = publisher.publish(new TestPayload("world", 99));

    assertThat(id).isEqualTo("id-2");
    ArgumentCaptor<StoredEvent> captor = ArgumentCaptor.forClass(StoredEvent.class);
    verify(journal).append(captor.capture(), eq(ENTRY_TTL));
    assertThat(captor.getValue().eventType()).isNull();
  }

  @Test
  void closeCompletesJournalWithDefaultRetention() {
    publisher.close();

    verify(journal).complete(RETENTION_TTL);
  }

  @Test
  void closeWithOverrideRetention() {
    Duration override = Duration.ofHours(2);

    publisher.close(override);

    verify(journal).complete(override);
  }

  @Test
  void deleteDeletesJournal() {
    publisher.delete();

    verify(journal).delete();
  }

  @Test
  void keyReturnsJournalKey() {
    assertThat(publisher.key()).isEqualTo("test-key");
  }

  @Test
  void tryWithResourcesCallsClose() {
    OdysseyPublisher<TestPayload> pub =
        new DefaultOdysseyPublisher<>(journal, objectMapper, ENTRY_TTL, RETENTION_TTL);
    try (pub) {
      when(journal.append(any(), eq(ENTRY_TTL))).thenReturn("id-3");
      pub.publish("test", new TestPayload("auto", 1));
    }

    verify(journal).complete(RETENTION_TTL);
  }
}
