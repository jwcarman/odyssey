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
package org.jwcarman.odyssey.eventlog.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

@ExtendWith(MockitoExtension.class)
class DynamoDbOdysseyEventLogTest {

  @Mock private DynamoDbClient client;

  private DynamoDbOdysseyEventLog eventLog;
  private DynamoDbOdysseyEventLog zeroTtlEventLog;

  @BeforeEach
  void setUp() {
    eventLog =
        new DynamoDbOdysseyEventLog(
            client,
            "odyssey-events",
            Duration.ofDays(7),
            "odyssey:ephemeral:",
            "odyssey:channel:",
            "odyssey:broadcast:");
    zeroTtlEventLog =
        new DynamoDbOdysseyEventLog(
            client,
            "odyssey-events",
            Duration.ZERO,
            "odyssey:ephemeral:",
            "odyssey:channel:",
            "odyssey:broadcast:");
  }

  @Test
  void appendWithNullMetadataDoesNotIncludeMetadataField() {
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType("test")
            .payload("data")
            .timestamp(Instant.now())
            .streamKey("odyssey:channel:test")
            .metadata(null)
            .build();

    eventLog.append("odyssey:channel:test", event);

    verify(client)
        .putItem(
            argThat(
                (PutItemRequest req) -> req.item() != null && !req.item().containsKey("metadata")));
  }

  @Test
  void appendWithEmptyMetadataDoesNotIncludeMetadataField() {
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType("test")
            .payload("data")
            .timestamp(Instant.now())
            .streamKey("odyssey:channel:test")
            .metadata(Map.of())
            .build();

    eventLog.append("odyssey:channel:test", event);

    verify(client)
        .putItem(
            argThat(
                (PutItemRequest req) -> req.item() != null && !req.item().containsKey("metadata")));
  }

  @Test
  void appendWithZeroTtlDoesNotIncludeTtlField() {
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType("test")
            .payload("data")
            .timestamp(Instant.now())
            .streamKey("odyssey:channel:test")
            .build();

    zeroTtlEventLog.append("odyssey:channel:test", event);

    verify(client)
        .putItem(
            argThat((PutItemRequest req) -> req.item() != null && !req.item().containsKey("ttl")));
  }

  @Test
  void mapItemWithoutMetadataReturnsEmptyMap() {
    Map<String, AttributeValue> item =
        Map.of(
            "stream_key", AttributeValue.builder().s("odyssey:channel:test").build(),
            "event_id", AttributeValue.builder().s("1-0").build(),
            "event_type", AttributeValue.builder().s("test").build(),
            "payload", AttributeValue.builder().s("data").build(),
            "timestamp", AttributeValue.builder().s("2026-04-06T12:00:00Z").build());

    QueryResponse response =
        QueryResponse.builder().items(List.of(item)).lastEvaluatedKey(Map.of()).build();

    when(client.query(any(QueryRequest.class))).thenReturn(response);

    Stream<OdysseyEvent> events = eventLog.readAfter("odyssey:channel:test", "0-0");
    List<OdysseyEvent> eventList = events.toList();

    assertThat(eventList).hasSize(1);
    assertThat(eventList.getFirst().metadata()).isEmpty();
  }

  @Test
  void createTableIgnoresResourceInUseException() {
    doThrow(ResourceInUseException.builder().message("Table already exists").build())
        .when(client)
        .createTable(any(Consumer.class));

    assertThatCode(() -> eventLog.createTable()).doesNotThrowAnyException();
  }
}
