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
package org.jwcarman.odyssey.eventlog.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class MongoOdysseyEventLogTest {

  @Mock private MongoTemplate mongoTemplate;

  private MongoOdysseyEventLog eventLog;
  private MongoOdysseyEventLog zeroTtlEventLog;

  @BeforeEach
  void setUp() {
    eventLog =
        new MongoOdysseyEventLog(
            mongoTemplate,
            "odyssey-events",
            Duration.ofDays(7),
            "odyssey:ephemeral:",
            "odyssey:channel:",
            "odyssey:broadcast:");
    zeroTtlEventLog =
        new MongoOdysseyEventLog(
            mongoTemplate,
            "odyssey-events",
            Duration.ZERO,
            "odyssey:ephemeral:",
            "odyssey:channel:",
            "odyssey:broadcast:");
  }

  @Test
  void appendWithNullMetadataDoesNotIncludeMetadataInDocument() {
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType("test")
            .payload("data")
            .timestamp(Instant.now())
            .streamKey("odyssey:channel:test")
            .metadata(null)
            .build();

    eventLog.append("odyssey:channel:test", event);

    verify(mongoTemplate)
        .insert(argThat((Document doc) -> !doc.containsKey("metadata")), anyString());
  }

  @Test
  void appendWithEmptyMetadataDoesNotIncludeMetadataInDocument() {
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType("test")
            .payload("data")
            .timestamp(Instant.now())
            .streamKey("odyssey:channel:test")
            .metadata(Map.of())
            .build();

    eventLog.append("odyssey:channel:test", event);

    verify(mongoTemplate)
        .insert(argThat((Document doc) -> !doc.containsKey("metadata")), anyString());
  }

  @Test
  void appendWithZeroTtlDoesNotIncludeExpireAt() {
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType("test")
            .payload("data")
            .timestamp(Instant.now())
            .streamKey("odyssey:channel:test")
            .build();

    zeroTtlEventLog.append("odyssey:channel:test", event);

    verify(mongoTemplate)
        .insert(argThat((Document doc) -> !doc.containsKey("expireAt")), anyString());
  }

  @Test
  void mapDocumentWithoutMetadataReturnsEmptyMap() {
    Document doc = new Document();
    doc.put("streamKey", "odyssey:channel:test");
    doc.put("eventId", "1-0");
    doc.put("eventType", "test");
    doc.put("payload", "data");
    doc.put("timestamp", "2026-04-06T12:00:00Z");

    when(mongoTemplate.find(any(Query.class), any(Class.class), anyString()))
        .thenReturn(List.of(doc));

    Stream<OdysseyEvent> events = eventLog.readAfter("odyssey:channel:test", "0-0");
    List<OdysseyEvent> eventList = events.toList();

    assertThat(eventList).hasSize(1);
    assertThat(eventList.getFirst().metadata()).isEmpty();
  }
}
