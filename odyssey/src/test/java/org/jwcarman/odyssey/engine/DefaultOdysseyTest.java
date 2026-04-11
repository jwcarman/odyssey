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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.autoconfigure.OdysseyProperties;
import org.jwcarman.odyssey.autoconfigure.SseProperties;
import org.jwcarman.odyssey.core.OdysseyPublisher;
import org.jwcarman.odyssey.core.SubscriberCustomizer;
import org.jwcarman.odyssey.core.TtlPolicy;
import org.jwcarman.substrate.journal.Journal;
import org.jwcarman.substrate.journal.JournalAlreadyExistsException;
import org.jwcarman.substrate.journal.JournalFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DefaultOdysseyTest {

  private static final TtlPolicy DEFAULT_TTL =
      new TtlPolicy(Duration.ofHours(1), Duration.ofHours(1), Duration.ofMinutes(5));
  private static final OdysseyProperties PROPS =
      new OdysseyProperties(DEFAULT_TTL, new SseProperties(Duration.ZERO, Duration.ofSeconds(30)));

  @Mock private JournalFactory journalFactory;
  @Mock private Journal<StoredEvent> journal;

  private final ObjectMapper objectMapper = new ObjectMapper();

  record TestEvent(String value) {}

  @Test
  void publisherCreatesJournalWithDefaultTtl() {
    when(journalFactory.create("my-stream", StoredEvent.class, Duration.ofHours(1)))
        .thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    OdysseyPublisher<TestEvent> pub = odyssey.publisher("my-stream", TestEvent.class);

    assertThat(pub).isNotNull();
    assertThat(pub.name()).isEqualTo("my-stream");
    verify(journalFactory).create("my-stream", StoredEvent.class, Duration.ofHours(1));
  }

  @Test
  void publisherFallsBackToConnectOnAlreadyExists() {
    when(journalFactory.create(eq("existing"), eq(StoredEvent.class), any(Duration.class)))
        .thenThrow(new JournalAlreadyExistsException("existing"));
    when(journalFactory.connect("existing", StoredEvent.class)).thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    OdysseyPublisher<TestEvent> pub = odyssey.publisher("existing", TestEvent.class);

    assertThat(pub).isNotNull();
    verify(journalFactory).connect("existing", StoredEvent.class);
  }

  @Test
  void perCallCustomizerOverridesDefaultTtl() {
    when(journalFactory.create(any(), eq(StoredEvent.class), eq(Duration.ofMinutes(30))))
        .thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    odyssey.publisher("tweaked", TestEvent.class, cfg -> cfg.inactivityTtl(Duration.ofMinutes(30)));

    verify(journalFactory).create(any(), eq(StoredEvent.class), eq(Duration.ofMinutes(30)));
  }

  @Test
  void perCallCustomizerCanReplaceAllThreeTtlsViaTtlPolicy() {
    TtlPolicy custom =
        new TtlPolicy(Duration.ofMinutes(2), Duration.ofMinutes(2), Duration.ofMinutes(2));
    when(journalFactory.create(any(), eq(StoredEvent.class), eq(Duration.ofMinutes(2))))
        .thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    odyssey.publisher("short-lived", TestEvent.class, cfg -> cfg.ttl(custom));

    verify(journalFactory).create(any(), eq(StoredEvent.class), eq(Duration.ofMinutes(2)));
  }

  @Test
  void subscribeReturnsEmitter() {
    when(journalFactory.connect("my-stream", StoredEvent.class)).thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    var emitter = odyssey.subscribe("my-stream", TestEvent.class);

    assertThat(emitter).isNotNull();
  }

  @Test
  void resumeReturnsEmitter() {
    when(journalFactory.connect("my-stream", StoredEvent.class)).thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    var emitter = odyssey.resume("my-stream", TestEvent.class, "last-id");

    assertThat(emitter).isNotNull();
  }

  @Test
  void replayReturnsEmitter() {
    when(journalFactory.connect("my-stream", StoredEvent.class)).thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    var emitter = odyssey.replay("my-stream", TestEvent.class, 10);

    assertThat(emitter).isNotNull();
  }

  @Test
  void perCallSubscriberCustomizerIsAppliedOnEveryStartMethod() {
    when(journalFactory.connect("my-stream", StoredEvent.class)).thenReturn(journal);

    AtomicInteger customizerCallCount = new AtomicInteger();
    SubscriberCustomizer<TestEvent> tracking =
        config -> {
          customizerCallCount.incrementAndGet();
          config.keepAliveInterval(Duration.ofSeconds(5));
        };

    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    odyssey.subscribe("my-stream", TestEvent.class, tracking);
    odyssey.resume("my-stream", TestEvent.class, "id-1", tracking);
    odyssey.replay("my-stream", TestEvent.class, 3, tracking);

    assertThat(customizerCallCount).hasValue(3);
  }

  @Test
  void nameRoundTripsThroughPublisher() {
    when(journalFactory.create(any(), eq(StoredEvent.class), any(Duration.class)))
        .thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    OdysseyPublisher<TestEvent> pub = odyssey.publisher("user:alice", TestEvent.class);

    // Flat namespace: what the caller passes is what pub.name() returns, verbatim.
    assertThat(pub.name()).isEqualTo("user:alice");
  }
}
