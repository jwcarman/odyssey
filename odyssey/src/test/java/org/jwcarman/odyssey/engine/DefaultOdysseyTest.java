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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.autoconfigure.OdysseyProperties;
import org.jwcarman.odyssey.autoconfigure.SseProperties;
import org.jwcarman.odyssey.core.OdysseyStream;
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
  void streamCreatesJournalWithExplicitTtl() {
    TtlPolicy custom =
        new TtlPolicy(Duration.ofMinutes(10), Duration.ofMinutes(10), Duration.ofMinutes(10));
    when(journalFactory.create("my-stream", StoredEvent.class, Duration.ofMinutes(10)))
        .thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    OdysseyStream<TestEvent> s = odyssey.stream("my-stream", TestEvent.class, custom);

    assertThat(s).isNotNull();
    assertThat(s.name()).isEqualTo("my-stream");
    verify(journalFactory).create("my-stream", StoredEvent.class, Duration.ofMinutes(10));
  }

  @Test
  void streamWithoutTtlUsesDefault() {
    when(journalFactory.create(any(), eq(StoredEvent.class), eq(Duration.ofHours(1))))
        .thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    OdysseyStream<TestEvent> s = odyssey.stream("default-ttl", TestEvent.class);

    assertThat(s).isNotNull();
    verify(journalFactory).create("default-ttl", StoredEvent.class, Duration.ofHours(1));
  }

  @Test
  void streamFallsBackToConnectOnAlreadyExists() {
    when(journalFactory.create(eq("existing"), eq(StoredEvent.class), any(Duration.class)))
        .thenThrow(new JournalAlreadyExistsException("existing"));
    when(journalFactory.connect("existing", StoredEvent.class)).thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    OdysseyStream<TestEvent> s = odyssey.stream("existing", TestEvent.class);

    assertThat(s).isNotNull();
    verify(journalFactory).connect("existing", StoredEvent.class);
  }

  @Test
  void nameRoundTripsVerbatim() {
    when(journalFactory.create(any(), eq(StoredEvent.class), any(Duration.class)))
        .thenReturn(journal);
    DefaultOdyssey odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    OdysseyStream<TestEvent> s = odyssey.stream("user:alice", TestEvent.class);

    // Flat namespace: what the caller passes is what stream.name() returns.
    assertThat(s.name()).isEqualTo("user:alice");
  }
}
