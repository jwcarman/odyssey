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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.AbstractOdysseyEventLog;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

public class DynamoDbOdysseyEventLog extends AbstractOdysseyEventLog {

  private static final int BATCH_DELETE_SIZE = 25;

  private final DynamoDbClient client;
  private final String tableName;
  private final Duration ttl;

  private long lastMillis = -1;
  private final AtomicInteger sequence = new AtomicInteger(0);

  public DynamoDbOdysseyEventLog(
      DynamoDbClient client,
      String tableName,
      Duration ttl,
      String ephemeralPrefix,
      String channelPrefix,
      String broadcastPrefix) {
    super(ephemeralPrefix, channelPrefix, broadcastPrefix);
    this.client = client;
    this.tableName = tableName;
    this.ttl = ttl;
  }

  public void createTable() {
    try {
      client.createTable(
          CreateTableRequest.builder()
              .tableName(tableName)
              .keySchema(
                  KeySchemaElement.builder()
                      .attributeName("stream_key")
                      .keyType(KeyType.HASH)
                      .build(),
                  KeySchemaElement.builder()
                      .attributeName("event_id")
                      .keyType(KeyType.RANGE)
                      .build())
              .attributeDefinitions(
                  AttributeDefinition.builder()
                      .attributeName("stream_key")
                      .attributeType(ScalarAttributeType.S)
                      .build(),
                  AttributeDefinition.builder()
                      .attributeName("event_id")
                      .attributeType(ScalarAttributeType.S)
                      .build())
              .provisionedThroughput(
                  ProvisionedThroughput.builder()
                      .readCapacityUnits(5L)
                      .writeCapacityUnits(5L)
                      .build())
              .build());
    } catch (ResourceInUseException ignored) {
      // table already exists
    }
  }

  @Override
  public String append(String streamKey, OdysseyEvent event) {
    String eventId = generateEventId();

    Map<String, AttributeValue> item = new HashMap<>();
    item.put("stream_key", AttributeValue.builder().s(streamKey).build());
    item.put("event_id", AttributeValue.builder().s(eventId).build());
    item.put("event_type", AttributeValue.builder().s(event.eventType()).build());
    item.put("payload", AttributeValue.builder().s(event.payload()).build());
    item.put("timestamp", AttributeValue.builder().s(event.timestamp().toString()).build());

    if (event.metadata() != null && !event.metadata().isEmpty()) {
      Map<String, AttributeValue> metadataMap = new HashMap<>();
      event.metadata().forEach((k, v) -> metadataMap.put(k, AttributeValue.builder().s(v).build()));
      item.put("metadata", AttributeValue.builder().m(metadataMap).build());
    }

    if (!ttl.isZero()) {
      long ttlEpoch = Instant.now().plus(ttl).getEpochSecond();
      item.put("ttl", AttributeValue.builder().n(String.valueOf(ttlEpoch)).build());
    }

    client.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());

    return eventId;
  }

  @Override
  public Stream<OdysseyEvent> readAfter(String streamKey, String lastId) {
    List<OdysseyEvent> events = new ArrayList<>();
    Map<String, AttributeValue> exclusiveStartKey = null;

    do {
      QueryRequest.Builder queryBuilder =
          QueryRequest.builder()
              .tableName(tableName)
              .keyConditionExpression("stream_key = :sk AND event_id > :eid")
              .expressionAttributeValues(
                  Map.of(
                      ":sk", AttributeValue.builder().s(streamKey).build(),
                      ":eid", AttributeValue.builder().s(lastId).build()))
              .scanIndexForward(true);

      if (exclusiveStartKey != null) {
        queryBuilder.exclusiveStartKey(exclusiveStartKey);
      }

      QueryResponse response = client.query(queryBuilder.build());
      for (Map<String, AttributeValue> item : response.items()) {
        events.add(mapItem(item));
      }
      exclusiveStartKey =
          response.lastEvaluatedKey().isEmpty() ? null : response.lastEvaluatedKey();
    } while (exclusiveStartKey != null);

    return events.stream();
  }

  @Override
  public Stream<OdysseyEvent> readLast(String streamKey, int count) {
    QueryResponse response =
        client.query(
            QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("stream_key = :sk")
                .expressionAttributeValues(
                    Map.of(":sk", AttributeValue.builder().s(streamKey).build()))
                .scanIndexForward(false)
                .limit(count)
                .build());

    List<OdysseyEvent> events = new ArrayList<>();
    for (Map<String, AttributeValue> item : response.items()) {
      events.add(mapItem(item));
    }
    Collections.reverse(events);
    return events.stream();
  }

  @Override
  public void delete(String streamKey) {
    Map<String, AttributeValue> exclusiveStartKey = null;

    do {
      QueryRequest.Builder queryBuilder =
          QueryRequest.builder()
              .tableName(tableName)
              .keyConditionExpression("stream_key = :sk")
              .expressionAttributeValues(
                  Map.of(":sk", AttributeValue.builder().s(streamKey).build()))
              .projectionExpression("stream_key, event_id");

      if (exclusiveStartKey != null) {
        queryBuilder.exclusiveStartKey(exclusiveStartKey);
      }

      QueryResponse response = client.query(queryBuilder.build());
      List<Map<String, AttributeValue>> items = response.items();

      for (int i = 0; i < items.size(); i += BATCH_DELETE_SIZE) {
        List<WriteRequest> writeRequests =
            items.subList(i, Math.min(i + BATCH_DELETE_SIZE, items.size())).stream()
                .map(
                    item ->
                        WriteRequest.builder()
                            .deleteRequest(
                                DeleteRequest.builder()
                                    .key(
                                        Map.of(
                                            "stream_key", item.get("stream_key"),
                                            "event_id", item.get("event_id")))
                                    .build())
                            .build())
                .toList();

        client.batchWriteItem(
            BatchWriteItemRequest.builder().requestItems(Map.of(tableName, writeRequests)).build());
      }

      exclusiveStartKey =
          response.lastEvaluatedKey().isEmpty() ? null : response.lastEvaluatedKey();
    } while (exclusiveStartKey != null);
  }

  private synchronized String generateEventId() {
    long millis = System.currentTimeMillis();
    if (millis == lastMillis) {
      return formatEventId(millis, sequence.incrementAndGet());
    } else {
      lastMillis = millis;
      sequence.set(0);
      return formatEventId(millis, 0);
    }
  }

  private String formatEventId(long millis, int seq) {
    return String.format("%013d-%05d", millis, seq);
  }

  private OdysseyEvent mapItem(Map<String, AttributeValue> item) {
    Map<String, String> metadata = Map.of();
    if (item.containsKey("metadata") && item.get("metadata").m() != null) {
      Map<String, String> metadataMap = new HashMap<>();
      item.get("metadata").m().forEach((k, v) -> metadataMap.put(k, v.s()));
      metadata = metadataMap;
    }

    return OdysseyEvent.builder()
        .id(item.get("event_id").s())
        .streamKey(item.get("stream_key").s())
        .eventType(item.get("event_type").s())
        .payload(item.get("payload").s())
        .timestamp(Instant.parse(item.get("timestamp").s()))
        .metadata(metadata)
        .build();
  }
}
