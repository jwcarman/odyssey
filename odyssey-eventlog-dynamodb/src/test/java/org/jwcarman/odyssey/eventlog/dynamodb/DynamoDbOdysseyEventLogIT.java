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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

@Testcontainers
class DynamoDbOdysseyEventLogIT {

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
          .withServices(LocalStackContainer.Service.DYNAMODB);

  private DynamoDbOdysseyEventLog eventLog;
  private DynamoDbClient client;

  @BeforeEach
  void setUp() {
    client =
        DynamoDbClient.builder()
            .endpointOverride(
                URI.create(
                    localstack
                        .getEndpointOverride(LocalStackContainer.Service.DYNAMODB)
                        .toString()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .region(Region.of(localstack.getRegion()))
            .build();

    try {
      client.deleteTable(DeleteTableRequest.builder().tableName("odyssey_events").build());
    } catch (ResourceNotFoundException ignored) {
      // table doesn't exist yet
    }

    eventLog =
        new DynamoDbOdysseyEventLog(
            client, "odyssey_events", Duration.ZERO, "ephemeral:", "channel:", "broadcast:");
    eventLog.createTable();
  }

  @Test
  void appendReturnsTimestampBasedId() {
    OdysseyEvent event = buildEvent("test-stream", "msg", "hello");
    String id = eventLog.append("test-stream", event);

    assertThat(id).matches("\\d{13}-\\d{5}");
  }

  @Test
  void appendReturnsMonotonicallyIncreasingIds() {
    OdysseyEvent event = buildEvent("test-stream", "msg", "hello");
    String id1 = eventLog.append("test-stream", event);
    String id2 = eventLog.append("test-stream", event);

    assertThat(id2).isGreaterThan(id1);
  }

  @Test
  void readAfterReturnsEventsInOrder() {
    String streamKey = "channel:orders";
    String id1 = eventLog.append(streamKey, buildEvent(streamKey, "order.created", "payload1"));
    String id2 = eventLog.append(streamKey, buildEvent(streamKey, "order.updated", "payload2"));
    String id3 = eventLog.append(streamKey, buildEvent(streamKey, "order.shipped", "payload3"));

    List<OdysseyEvent> events = eventLog.readAfter(streamKey, id1).toList();

    assertThat(events).hasSize(2);
    assertThat(events.get(0).id()).isEqualTo(id2);
    assertThat(events.get(0).eventType()).isEqualTo("order.updated");
    assertThat(events.get(1).id()).isEqualTo(id3);
    assertThat(events.get(1).eventType()).isEqualTo("order.shipped");
  }

  @Test
  void readAfterReturnsEmptyForUnknownStream() {
    List<OdysseyEvent> events = eventLog.readAfter("nonexistent", "0000000000000-00000").toList();
    assertThat(events).isEmpty();
  }

  @Test
  void readLastReturnsLastNInChronologicalOrder() {
    String streamKey = "broadcast:announcements";
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "first"));
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "second"));
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "third"));
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "fourth"));

    List<OdysseyEvent> events = eventLog.readLast(streamKey, 2).toList();

    assertThat(events).hasSize(2);
    assertThat(events.get(0).payload()).isEqualTo("third");
    assertThat(events.get(1).payload()).isEqualTo("fourth");
  }

  @Test
  void readLastReturnsEmptyForUnknownStream() {
    List<OdysseyEvent> events = eventLog.readLast("nonexistent", 5).toList();
    assertThat(events).isEmpty();
  }

  @Test
  void deleteRemovesAllEventsForStream() {
    String streamKey = "ephemeral:abc";
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "hello"));
    eventLog.append(streamKey, buildEvent(streamKey, "msg", "world"));

    eventLog.delete(streamKey);

    List<OdysseyEvent> events = eventLog.readLast(streamKey, 100).toList();
    assertThat(events).isEmpty();
  }

  @Test
  void deleteDoesNotAffectOtherStreams() {
    String stream1 = "channel:a";
    String stream2 = "channel:b";
    eventLog.append(stream1, buildEvent(stream1, "msg", "a-event"));
    eventLog.append(stream2, buildEvent(stream2, "msg", "b-event"));

    eventLog.delete(stream1);

    assertThat(eventLog.readLast(stream1, 100).toList()).isEmpty();
    assertThat(eventLog.readLast(stream2, 100).toList()).hasSize(1);
  }

  @Test
  void metadataIsPreserved() {
    String streamKey = "channel:meta";
    Map<String, String> metadata = Map.of("userId", "42", "source", "api");
    OdysseyEvent event =
        OdysseyEvent.builder()
            .streamKey(streamKey)
            .eventType("test")
            .payload("data")
            .timestamp(Instant.now())
            .metadata(metadata)
            .build();

    eventLog.append(streamKey, event);

    List<OdysseyEvent> events = eventLog.readLast(streamKey, 1).toList();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().metadata()).containsEntry("userId", "42");
    assertThat(events.getFirst().metadata()).containsEntry("source", "api");
  }

  @Test
  void timestampIsPreserved() {
    String streamKey = "channel:time";
    Instant now = Instant.now();
    OdysseyEvent event =
        OdysseyEvent.builder()
            .streamKey(streamKey)
            .eventType("test")
            .payload("data")
            .timestamp(now)
            .build();

    eventLog.append(streamKey, event);

    List<OdysseyEvent> events = eventLog.readLast(streamKey, 1).toList();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().timestamp()).isEqualTo(now);
  }

  @Test
  void appendWithTtlDoesNotError() {
    DynamoDbOdysseyEventLog ttlLog =
        new DynamoDbOdysseyEventLog(
            client, "odyssey_events", Duration.ofHours(1), "ephemeral:", "channel:", "broadcast:");

    String streamKey = "ephemeral:ttl-test";
    String id = ttlLog.append(streamKey, buildEvent(streamKey, "msg", "ttl-event"));
    assertThat(id).isNotEmpty();

    List<OdysseyEvent> events = ttlLog.readLast(streamKey, 1).toList();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().payload()).isEqualTo("ttl-event");
  }

  @Test
  void ephemeralKeyGeneratesUniqueKeys() {
    String key1 = eventLog.ephemeralKey();
    String key2 = eventLog.ephemeralKey();

    assertThat(key1).startsWith("ephemeral:");
    assertThat(key2).startsWith("ephemeral:");
    assertThat(key1).isNotEqualTo(key2);
  }

  @Test
  void channelKeyUsesPrefix() {
    assertThat(eventLog.channelKey("orders")).isEqualTo("channel:orders");
  }

  @Test
  void broadcastKeyUsesPrefix() {
    assertThat(eventLog.broadcastKey("announcements")).isEqualTo("broadcast:announcements");
  }

  private static OdysseyEvent buildEvent(String streamKey, String eventType, String payload) {
    return OdysseyEvent.builder()
        .streamKey(streamKey)
        .eventType(eventType)
        .payload(payload)
        .timestamp(Instant.now())
        .build();
  }
}
