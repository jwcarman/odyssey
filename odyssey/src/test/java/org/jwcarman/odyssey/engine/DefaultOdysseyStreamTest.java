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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.autoconfigure.OdysseyProperties;
import org.jwcarman.odyssey.autoconfigure.SseProperties;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.SubscriberCustomizer;
import org.jwcarman.odyssey.core.TtlPolicy;
import org.jwcarman.substrate.journal.Journal;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DefaultOdysseyStreamTest {

  private static final TtlPolicy TTL =
      new TtlPolicy(Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofMinutes(1));
  private static final OdysseyProperties PROPS =
      new OdysseyProperties(TTL, new SseProperties(Duration.ZERO, Duration.ofSeconds(30)));

  @Mock private Journal<StoredEvent> journal;

  private final ObjectMapper objectMapper = new ObjectMapper();

  record TestEvent(String value) {}

  private OdysseyStream<TestEvent> stream;

  @BeforeEach
  void setUp() {
    stream =
        new DefaultOdysseyStream<>(journal, "my-stream", TestEvent.class, TTL, objectMapper, PROPS);
  }

  @Test
  void nameReturnsVerbatim() {
    assertThat(stream.name()).isEqualTo("my-stream");
  }

  @Test
  void publishWithEventTypeAppendsWithEntryTtl() {
    when(journal.append(any(StoredEvent.class), eq(Duration.ofMinutes(5)))).thenReturn("id-1");

    String id = stream.publish("created", new TestEvent("hello"));

    assertThat(id).isEqualTo("id-1");
    verify(journal).append(any(StoredEvent.class), eq(Duration.ofMinutes(5)));
  }

  @Test
  void publishWithoutEventTypeAppendsWithNullEventType() {
    when(journal.append(any(StoredEvent.class), any(Duration.class))).thenReturn("id-2");

    String id = stream.publish(new TestEvent("no-type"));

    assertThat(id).isEqualTo("id-2");
  }

  @Test
  void completeUsesRetentionTtl() {
    stream.complete();
    verify(journal).complete(Duration.ofMinutes(1));
  }

  @Test
  void deleteForwardsToJournal() {
    stream.delete();
    verify(journal).delete();
  }

  @Test
  void subscribeReturnsEmitter() {
    assertThat(stream.subscribe()).isNotNull();
  }

  @Test
  void resumeReturnsEmitter() {
    assertThat(stream.resume("last-id")).isNotNull();
  }

  @Test
  void replayReturnsEmitter() {
    assertThat(stream.replay(5)).isNotNull();
  }

  @Test
  void subscriberCustomizerRunsOnEverySubscribeShape() {
    AtomicInteger calls = new AtomicInteger();
    SubscriberCustomizer<TestEvent> tracking =
        cfg -> {
          calls.incrementAndGet();
          cfg.keepAliveInterval(Duration.ofSeconds(5));
        };

    stream.subscribe(tracking);
    stream.resume("id-1", tracking);
    stream.replay(3, tracking);

    assertThat(calls).hasValue(3);
  }
}
