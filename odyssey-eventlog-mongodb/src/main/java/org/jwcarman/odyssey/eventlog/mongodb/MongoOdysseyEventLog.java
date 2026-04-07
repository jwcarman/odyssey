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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.bson.Document;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.AbstractOdysseyEventLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class MongoOdysseyEventLog extends AbstractOdysseyEventLog {

  private static final String FIELD_STREAM_KEY = "streamKey";
  private static final String FIELD_EVENT_ID = "eventId";
  private static final String FIELD_METADATA = "metadata";

  private final MongoTemplate mongoTemplate;
  private final String collectionName;
  private final Duration ttl;

  public MongoOdysseyEventLog(
      MongoTemplate mongoTemplate,
      String collectionName,
      Duration ttl,
      String ephemeralPrefix,
      String channelPrefix,
      String broadcastPrefix) {
    super(ephemeralPrefix, channelPrefix, broadcastPrefix);
    this.mongoTemplate = mongoTemplate;
    this.collectionName = collectionName;
    this.ttl = ttl;
  }

  public void ensureIndexes() {
    var indexOps = mongoTemplate.indexOps(collectionName);

    // Compound index for cursor-based reads
    indexOps.createIndex(
        new CompoundIndexDefinition(new Document(FIELD_STREAM_KEY, 1).append(FIELD_EVENT_ID, 1)));

    // TTL index for automatic expiration
    if (!ttl.isZero()) {
      indexOps.createIndex(new Index().on("expireAt", Sort.Direction.ASC).expire(0));
    }
  }

  @Override
  public String append(String streamKey, OdysseyEvent event) {
    String eventId = generateEventId();

    Document doc = new Document();
    doc.put(FIELD_STREAM_KEY, streamKey);
    doc.put(FIELD_EVENT_ID, eventId);
    doc.put("eventType", event.eventType());
    doc.put("payload", event.payload());
    doc.put("timestamp", event.timestamp().toString());
    doc.put("createdAt", Instant.now());

    if (event.metadata() != null && !event.metadata().isEmpty()) {
      doc.put(FIELD_METADATA, new Document(new HashMap<>(event.metadata())));
    }

    if (!ttl.isZero()) {
      doc.put("expireAt", Instant.now().plus(ttl));
    }

    mongoTemplate.insert(doc, collectionName);

    return eventId;
  }

  @Override
  public Stream<OdysseyEvent> readAfter(String streamKey, String lastId) {
    Query query =
        new Query(Criteria.where(FIELD_STREAM_KEY).is(streamKey).and(FIELD_EVENT_ID).gt(lastId))
            .with(Sort.by(Sort.Direction.ASC, FIELD_EVENT_ID));

    List<Document> docs = mongoTemplate.find(query, Document.class, collectionName);
    return docs.stream().map(this::mapDocument);
  }

  @Override
  public Stream<OdysseyEvent> readLast(String streamKey, int count) {
    Query query =
        new Query(Criteria.where(FIELD_STREAM_KEY).is(streamKey))
            .with(Sort.by(Sort.Direction.DESC, FIELD_EVENT_ID))
            .limit(count);

    List<Document> docs = mongoTemplate.find(query, Document.class, collectionName);
    return docs.reversed().stream().map(this::mapDocument);
  }

  @Override
  public void delete(String streamKey) {
    Query query = new Query(Criteria.where(FIELD_STREAM_KEY).is(streamKey));
    mongoTemplate.remove(query, collectionName);
  }

  private OdysseyEvent mapDocument(Document doc) {
    Map<String, String> metadata = Map.of();
    if (doc.containsKey(FIELD_METADATA)) {
      Document metaDoc = doc.get(FIELD_METADATA, Document.class);
      if (metaDoc != null) {
        Map<String, String> metadataMap = new HashMap<>();
        metaDoc.forEach((k, v) -> metadataMap.put(k, String.valueOf(v)));
        metadata = metadataMap;
      }
    }

    return OdysseyEvent.builder()
        .id(doc.getString(FIELD_EVENT_ID))
        .streamKey(doc.getString(FIELD_STREAM_KEY))
        .eventType(doc.getString("eventType"))
        .payload(doc.getString("payload"))
        .timestamp(Instant.parse(doc.getString("timestamp")))
        .metadata(metadata)
        .build();
  }
}
