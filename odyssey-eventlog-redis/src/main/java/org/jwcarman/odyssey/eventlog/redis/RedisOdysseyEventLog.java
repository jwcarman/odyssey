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
import org.jwcarman.odyssey.spi.OdysseyEventLog;

public class RedisOdysseyEventLog implements OdysseyEventLog {

  private static final int READ_BATCH_SIZE = 100;

  private final RedisCommands<String, String> commands;
  private final long maxLen;
  private final String streamPrefix;
  private final long ephemeralTtlSeconds;
  private final long channelTtlSeconds;
  private final long broadcastTtlSeconds;

  public RedisOdysseyEventLog(
      RedisCommands<String, String> commands,
      long maxLen,
      String streamPrefix,
      long ephemeralTtlSeconds,
      long channelTtlSeconds,
      long broadcastTtlSeconds) {
    this.commands = commands;
    this.maxLen = maxLen;
    this.streamPrefix = streamPrefix;
    this.ephemeralTtlSeconds = ephemeralTtlSeconds;
    this.channelTtlSeconds = channelTtlSeconds;
    this.broadcastTtlSeconds = broadcastTtlSeconds;
  }

  @Override
  public String append(String streamKey, OdysseyEvent event) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("eventType", event.eventType());
    body.put("payload", event.payload());
    body.put("timestamp", event.timestamp().toString());
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
    String afterPrefix =
        streamKey.startsWith(streamPrefix) ? streamKey.substring(streamPrefix.length()) : streamKey;
    if (afterPrefix.startsWith("ephemeral:")) {
      return ephemeralTtlSeconds;
    } else if (afterPrefix.startsWith("channel:")) {
      return channelTtlSeconds;
    } else if (afterPrefix.startsWith("broadcast:")) {
      return broadcastTtlSeconds;
    }
    return 0;
  }

  private OdysseyEvent toOdysseyEvent(String streamKey, StreamMessage<String, String> message) {
    Map<String, String> body = message.getBody();
    String eventType = body.get("eventType");
    String payload = body.get("payload");
    String timestampStr = body.get("timestamp");
    Instant timestamp = timestampStr != null ? Instant.parse(timestampStr) : Instant.now();

    Map<String, String> metadata = new LinkedHashMap<>(body);
    metadata.remove("eventType");
    metadata.remove("payload");
    metadata.remove("timestamp");

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
