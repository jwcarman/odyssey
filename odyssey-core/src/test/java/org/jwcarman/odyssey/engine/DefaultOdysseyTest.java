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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.autoconfigure.OdysseyProperties;
import org.jwcarman.odyssey.core.OdysseyPublisher;
import org.jwcarman.odyssey.core.PublisherCustomizer;
import org.jwcarman.substrate.journal.Journal;
import org.jwcarman.substrate.journal.JournalAlreadyExistsException;
import org.jwcarman.substrate.journal.JournalFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DefaultOdysseyTest {

  private static final OdysseyProperties PROPS =
      new OdysseyProperties(
          Duration.ofSeconds(30),
          Duration.ZERO,
          Duration.ofMinutes(5),
          Duration.ofHours(1),
          Duration.ofHours(24));

  @Mock private JournalFactory journalFactory;
  @Mock private Journal<StoredEvent> journal;

  private final ObjectMapper objectMapper = new ObjectMapper();

  record TestEvent(String value) {}

  private void stubJournalKey() {
    when(journal.key()).thenReturn("test-key");
  }

  @Test
  void publisherCreatesJournal() {
    stubJournalKey();
    when(journalFactory.create(eq("my-key"), eq(StoredEvent.class), any(Duration.class)))
        .thenReturn(journal);
    DefaultOdyssey odyssey =
        new DefaultOdyssey(journalFactory, objectMapper, PROPS, List.of(), List.of());

    OdysseyPublisher<TestEvent> pub = odyssey.publisher("my-key", TestEvent.class);

    assertThat(pub).isNotNull();
    assertThat(pub.key()).isEqualTo("test-key");
  }

  @Test
  void publisherFallsBackToConnectOnAlreadyExists() {
    when(journalFactory.create(eq("my-key"), eq(StoredEvent.class), any(Duration.class)))
        .thenThrow(new JournalAlreadyExistsException("my-key"));
    when(journalFactory.connect("my-key", StoredEvent.class)).thenReturn(journal);
    DefaultOdyssey odyssey =
        new DefaultOdyssey(journalFactory, objectMapper, PROPS, List.of(), List.of());

    OdysseyPublisher<TestEvent> pub = odyssey.publisher("my-key", TestEvent.class);

    assertThat(pub).isNotNull();
    verify(journalFactory).connect("my-key", StoredEvent.class);
  }

  @Test
  void ephemeralUsesEphemeralTtl() {
    when(journalFactory.create(any(), eq(StoredEvent.class), eq(Duration.ofMinutes(5))))
        .thenReturn(journal);
    DefaultOdyssey odyssey =
        new DefaultOdyssey(journalFactory, objectMapper, PROPS, List.of(), List.of());

    OdysseyPublisher<TestEvent> pub = odyssey.ephemeral(TestEvent.class);

    assertThat(pub).isNotNull();
    verify(journalFactory).create(any(), eq(StoredEvent.class), eq(Duration.ofMinutes(5)));
  }

  @Test
  void channelUsesChannelTtl() {
    when(journalFactory.create("channel:orders", StoredEvent.class, Duration.ofHours(1)))
        .thenReturn(journal);
    DefaultOdyssey odyssey =
        new DefaultOdyssey(journalFactory, objectMapper, PROPS, List.of(), List.of());

    OdysseyPublisher<TestEvent> pub = odyssey.channel("orders", TestEvent.class);

    assertThat(pub).isNotNull();
    verify(journalFactory).create("channel:orders", StoredEvent.class, Duration.ofHours(1));
  }

  @Test
  void broadcastUsesBroadcastTtl() {
    when(journalFactory.create("broadcast:news", StoredEvent.class, Duration.ofHours(24)))
        .thenReturn(journal);
    DefaultOdyssey odyssey =
        new DefaultOdyssey(journalFactory, objectMapper, PROPS, List.of(), List.of());

    OdysseyPublisher<TestEvent> pub = odyssey.broadcast("news", TestEvent.class);

    assertThat(pub).isNotNull();
    verify(journalFactory).create("broadcast:news", StoredEvent.class, Duration.ofHours(24));
  }

  @Test
  void publisherCustomizerRunsBeforePerCallCustomizer() {
    when(journalFactory.create(any(), eq(StoredEvent.class), any(Duration.class)))
        .thenReturn(journal);

    PublisherCustomizer globalCustomizer = cfg -> cfg.entryTtl(Duration.ofMinutes(10));

    DefaultOdyssey odyssey =
        new DefaultOdyssey(
            journalFactory, objectMapper, PROPS, List.of(globalCustomizer), List.of());

    OdysseyPublisher<TestEvent> pub =
        odyssey.publisher("test", TestEvent.class, cfg -> cfg.entryTtl(Duration.ofMinutes(20)));

    assertThat(pub).isNotNull();
  }

  @Test
  void subscribeReturnsEmitter() {
    when(journalFactory.connect("my-key", StoredEvent.class)).thenReturn(journal);
    DefaultOdyssey odyssey =
        new DefaultOdyssey(journalFactory, objectMapper, PROPS, List.of(), List.of());

    var emitter = odyssey.subscribe("my-key", TestEvent.class);

    assertThat(emitter).isNotNull();
  }

  @Test
  void resumeReturnsEmitter() {
    when(journalFactory.connect("my-key", StoredEvent.class)).thenReturn(journal);
    DefaultOdyssey odyssey =
        new DefaultOdyssey(journalFactory, objectMapper, PROPS, List.of(), List.of());

    var emitter = odyssey.resume("my-key", TestEvent.class, "last-id");

    assertThat(emitter).isNotNull();
  }

  @Test
  void replayReturnsEmitter() {
    when(journalFactory.connect("my-key", StoredEvent.class)).thenReturn(journal);
    DefaultOdyssey odyssey =
        new DefaultOdyssey(journalFactory, objectMapper, PROPS, List.of(), List.of());

    var emitter = odyssey.replay("my-key", TestEvent.class, 10);

    assertThat(emitter).isNotNull();
  }

  @Test
  void perCallCustomizerOverridesCategory() {
    when(journalFactory.create(any(), eq(StoredEvent.class), eq(Duration.ofMinutes(30))))
        .thenReturn(journal);
    DefaultOdyssey odyssey =
        new DefaultOdyssey(journalFactory, objectMapper, PROPS, List.of(), List.of());

    odyssey.channel("test", TestEvent.class, cfg -> cfg.inactivityTtl(Duration.ofMinutes(30)));

    verify(journalFactory).create(any(), eq(StoredEvent.class), eq(Duration.ofMinutes(30)));
  }
}
