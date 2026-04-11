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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.odyssey.autoconfigure.OdysseyProperties;
import org.jwcarman.odyssey.autoconfigure.SseProperties;
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.TtlPolicy;
import org.jwcarman.substrate.BlockingSubscription;
import org.jwcarman.substrate.NextResult;
import org.jwcarman.substrate.core.journal.DefaultJournalFactory;
import org.jwcarman.substrate.core.lifecycle.ShutdownCoordinator;
import org.jwcarman.substrate.core.memory.journal.InMemoryJournalSpi;
import org.jwcarman.substrate.core.memory.notifier.InMemoryNotifier;
import org.jwcarman.substrate.journal.Journal;
import org.jwcarman.substrate.journal.JournalEntry;
import org.jwcarman.substrate.journal.JournalFactory;
import tools.jackson.databind.ObjectMapper;

class InMemoryEndToEndTest {

  private static final TtlPolicy DEFAULT_TTL =
      new TtlPolicy(Duration.ofHours(1), Duration.ofHours(1), Duration.ofMinutes(5));
  private static final OdysseyProperties PROPS =
      new OdysseyProperties(DEFAULT_TTL, new SseProperties(Duration.ZERO, Duration.ofMillis(500)));

  record OrderEvent(String orderId, String status) {}

  private JournalFactory journalFactory;
  private Odyssey odyssey;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();
    InMemoryJournalSpi journalSpi = new InMemoryJournalSpi(100);
    InMemoryNotifier notifier = new InMemoryNotifier();
    journalFactory =
        new DefaultJournalFactory(
            journalSpi,
            new JacksonCodecFactory(objectMapper),
            notifier,
            1024,
            Duration.ofDays(30),
            Duration.ofDays(30),
            Duration.ofDays(30),
            new ShutdownCoordinator());
    odyssey = new DefaultOdyssey(journalFactory, objectMapper, PROPS);
  }

  @Test
  void publishAndReadThroughJournal() {
    var pub = odyssey.publisher("orders", OrderEvent.class);
    pub.publish("order.created", new OrderEvent("o1", "created"));
    String id = pub.publish("order.shipped", new OrderEvent("o1", "shipped"));

    Journal<StoredEvent> journal = journalFactory.connect("orders", StoredEvent.class);
    BlockingSubscription<JournalEntry<StoredEvent>> sub = journal.subscribeAfter(id);
    pub.publish("order.delivered", new OrderEvent("o1", "delivered"));

    NextResult<JournalEntry<StoredEvent>> result = sub.next(Duration.ofSeconds(2));
    assertThat(result).isInstanceOf(NextResult.Value.class);
    StoredEvent stored = ((NextResult.Value<JournalEntry<StoredEvent>>) result).value().data();
    assertThat(stored.eventType()).isEqualTo("order.delivered");
    assertThat(stored.data()).contains("delivered");
    sub.cancel();
  }

  @Test
  void nameRoundTripsWithoutPrefix() {
    var pub = odyssey.publisher("user:123", OrderEvent.class);
    assertThat(pub.name()).isEqualTo("user:123");
  }

  @Test
  void subscribeReturnsValidEmitter() {
    var pub = odyssey.publisher("test", OrderEvent.class);
    pub.publish("test", new OrderEvent("o1", "ok"));

    var emitter = odyssey.subscribe(pub.name(), OrderEvent.class);
    assertThat(emitter).isNotNull();
  }

  @Test
  void twoOdysseyInstancesSeeEachOthersEvents() {
    ObjectMapper objectMapper = new ObjectMapper();
    Odyssey odyssey2 = new DefaultOdyssey(journalFactory, objectMapper, PROPS);

    var pub1 = odyssey.publisher("shared", OrderEvent.class);
    pub1.publish("from-1", new OrderEvent("o1", "instance1"));

    var pub2 = odyssey2.publisher("shared", OrderEvent.class);
    pub2.publish("from-2", new OrderEvent("o2", "instance2"));

    Journal<StoredEvent> journal = journalFactory.connect("shared", StoredEvent.class);
    BlockingSubscription<JournalEntry<StoredEvent>> sub = journal.subscribeLast(10);
    List<String> eventTypes = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      NextResult<JournalEntry<StoredEvent>> result = sub.next(Duration.ofSeconds(2));
      if (result instanceof NextResult.Value<JournalEntry<StoredEvent>> v) {
        eventTypes.add(v.value().data().eventType());
      }
    }
    sub.cancel();
    pub1.complete();
    pub2.complete();

    assertThat(eventTypes).containsExactly("from-1", "from-2");
  }

  @Test
  void deleteRemovesJournal() {
    var pub = odyssey.publisher("delete-test", OrderEvent.class);
    pub.publish("test", new OrderEvent("o1", "created"));

    Journal<StoredEvent> journal = journalFactory.connect("delete-test", StoredEvent.class);
    BlockingSubscription<JournalEntry<StoredEvent>> sub = journal.subscribeLast(10);
    NextResult<JournalEntry<StoredEvent>> first = sub.next(Duration.ofSeconds(1));
    assertThat(first).isInstanceOf(NextResult.Value.class);

    pub.delete();

    NextResult<JournalEntry<StoredEvent>> result = sub.next(Duration.ofSeconds(2));
    assertThat(result).isInstanceOfAny(NextResult.Deleted.class, NextResult.Expired.class);
    sub.cancel();
  }

  @Test
  void publishWithoutEventType() {
    var pub = odyssey.publisher("no-type", OrderEvent.class);
    String id = pub.publish(new OrderEvent("o1", "created"));
    assertThat(id).isNotNull();

    Journal<StoredEvent> journal = journalFactory.connect("no-type", StoredEvent.class);
    BlockingSubscription<JournalEntry<StoredEvent>> sub = journal.subscribeLast(1);
    NextResult<JournalEntry<StoredEvent>> result = sub.next(Duration.ofSeconds(2));
    assertThat(result).isInstanceOf(NextResult.Value.class);
    StoredEvent stored = ((NextResult.Value<JournalEntry<StoredEvent>>) result).value().data();
    assertThat(stored.eventType()).isNull();
    sub.cancel();
  }

  @Test
  void publisherKeyRoundTripsThroughConnect() {
    // Regression: publisher.name() must round-trip back to the same underlying journal via
    // the journal factory. Previously the publisher returned Substrate's internal backend
    // key which would get double-prefixed on connect, landing on a different journal.
    var pub = odyssey.publisher("round-trip-test", OrderEvent.class);
    pub.publish("created", new OrderEvent("o1", "created"));

    Journal<StoredEvent> sameJournal = journalFactory.connect(pub.name(), StoredEvent.class);
    BlockingSubscription<JournalEntry<StoredEvent>> sub = sameJournal.subscribeLast(1);
    NextResult<JournalEntry<StoredEvent>> result = sub.next(Duration.ofSeconds(2));
    assertThat(result).isInstanceOf(NextResult.Value.class);
    StoredEvent stored = ((NextResult.Value<JournalEntry<StoredEvent>>) result).value().data();
    assertThat(stored.eventType()).isEqualTo("created");
    sub.cancel();
  }

  @Test
  void perCallCustomizerAppliesShortTtlPolicy() {
    TtlPolicy shortLived =
        new TtlPolicy(Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofSeconds(10));
    var pub =
        odyssey.publisher(
            java.util.UUID.randomUUID().toString(), OrderEvent.class, cfg -> cfg.ttl(shortLived));
    String id = pub.publish("created", new OrderEvent("o1", "created"));
    assertThat(id).isNotNull();
  }
}
