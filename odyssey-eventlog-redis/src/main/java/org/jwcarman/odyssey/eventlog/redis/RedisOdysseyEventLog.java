package org.jwcarman.odyssey.eventlog.redis;

import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.XReadArgs.StreamOffset;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.AbstractOdysseyEventLog;

public class RedisOdysseyEventLog extends AbstractOdysseyEventLog {

  private static final int READ_BATCH_SIZE = 100;
  private static final String FIELD_EVENT_TYPE = "eventType";
  private static final String FIELD_PAYLOAD = "payload";
  private static final String FIELD_TIMESTAMP = "timestamp";

  private final RedisCommands<String, String> commands;
  private final long maxLen;
  private final long ephemeralTtlSeconds;
  private final long channelTtlSeconds;
  private final long broadcastTtlSeconds;

  public RedisOdysseyEventLog(
      RedisCommands<String, String> commands,
      long maxLen,
      String ephemeralPrefix,
      String channelPrefix,
      String broadcastPrefix,
      long ephemeralTtlSeconds,
      long channelTtlSeconds,
      long broadcastTtlSeconds) {
    super(ephemeralPrefix, channelPrefix, broadcastPrefix);
    this.commands = commands;
    this.maxLen = maxLen;
    this.ephemeralTtlSeconds = ephemeralTtlSeconds;
    this.channelTtlSeconds = channelTtlSeconds;
    this.broadcastTtlSeconds = broadcastTtlSeconds;
  }

  @Override
  public String append(String streamKey, OdysseyEvent event) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put(FIELD_EVENT_TYPE, event.eventType());
    body.put(FIELD_PAYLOAD, event.payload());
    body.put(FIELD_TIMESTAMP, event.timestamp().toString());
    for (Map.Entry<String, String> entry : event.metadata().entrySet()) {
      body.put(entry.getKey(), entry.getValue());
    }

    XAddArgs args = new XAddArgs().maxlen(maxLen).approximateTrimming();
    String entryId = commands.xadd(streamKey, args, body);

    long ttlSeconds = resolveTtl(streamKey);
    if (ttlSeconds > 0) {
      commands.expire(streamKey, ttlSeconds);
    }

    return entryId;
  }

  @Override
  public Stream<OdysseyEvent> readAfter(String streamKey, String lastId) {
    List<StreamMessage<String, String>> messages =
        commands.xread(
            XReadArgs.Builder.count(READ_BATCH_SIZE), StreamOffset.from(streamKey, lastId));
    if (messages == null || messages.isEmpty()) {
      return Stream.empty();
    }
    return messages.stream().map(msg -> toOdysseyEvent(streamKey, msg));
  }

  @Override
  public Stream<OdysseyEvent> readLast(String streamKey, int count) {
    List<StreamMessage<String, String>> messages =
        new ArrayList<>(commands.xrevrange(streamKey, Range.unbounded(), Limit.create(0, count)));
    Collections.reverse(messages);
    return messages.stream().map(msg -> toOdysseyEvent(streamKey, msg));
  }

  @Override
  public void delete(String streamKey) {
    commands.del(streamKey);
  }

  private long resolveTtl(String streamKey) {
    if (streamKey.startsWith(ephemeralPrefix())) {
      return ephemeralTtlSeconds;
    } else if (streamKey.startsWith(channelPrefix())) {
      return channelTtlSeconds;
    } else if (streamKey.startsWith(broadcastPrefix())) {
      return broadcastTtlSeconds;
    }
    return 0;
  }

  private OdysseyEvent toOdysseyEvent(String streamKey, StreamMessage<String, String> message) {
    Map<String, String> body = message.getBody();
    String eventType = body.get(FIELD_EVENT_TYPE);
    String payload = body.get(FIELD_PAYLOAD);
    String timestampStr = body.get(FIELD_TIMESTAMP);
    Instant timestamp = timestampStr != null ? Instant.parse(timestampStr) : Instant.now();

    Map<String, String> metadata = new LinkedHashMap<>(body);
    metadata.remove(FIELD_EVENT_TYPE);
    metadata.remove(FIELD_PAYLOAD);
    metadata.remove(FIELD_TIMESTAMP);

    return OdysseyEvent.builder()
        .id(message.getId())
        .streamKey(streamKey)
        .eventType(eventType)
        .payload(payload)
        .timestamp(timestamp)
        .metadata(metadata.isEmpty() ? Map.of() : metadata)
        .build();
  }
}
