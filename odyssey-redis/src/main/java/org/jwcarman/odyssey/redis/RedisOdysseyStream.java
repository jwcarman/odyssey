package org.jwcarman.odyssey.redis;

import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.XReadArgs.StreamOffset;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RedisOdysseyStream implements OdysseyStream {

  private final String streamKey;
  private final RedisCommands<String, String> commands;
  private final TopicFanout fanout;
  private final long keepAliveInterval;
  private final long defaultSseTimeout;
  private final long maxLen;
  private final int maxLastN;
  private final String notifyChannel;
  private final long ttlSeconds;

  RedisOdysseyStream(
      String streamKey,
      RedisCommands<String, String> commands,
      TopicFanout fanout,
      long keepAliveInterval,
      long defaultSseTimeout,
      long maxLen,
      int maxLastN,
      String streamPrefix,
      long ttlSeconds) {
    this.streamKey = streamKey;
    this.commands = commands;
    this.fanout = fanout;
    this.keepAliveInterval = keepAliveInterval;
    this.defaultSseTimeout = defaultSseTimeout;
    this.maxLen = maxLen;
    this.maxLastN = maxLastN;
    this.notifyChannel = streamPrefix + "notify:" + streamKey;
    this.ttlSeconds = ttlSeconds;
  }

  @Override
  public String publish(String eventType, String payload) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("eventType", eventType);
    body.put("payload", payload);
    body.put("timestamp", Instant.now().toString());

    XAddArgs args = new XAddArgs().maxlen(maxLen).approximateTrimming();
    String entryId = commands.xadd(streamKey, args, body);
    commands.publish(notifyChannel, entryId);
    if (ttlSeconds > 0) {
      commands.expire(streamKey, ttlSeconds);
    }
    return entryId;
  }

  @Override
  public SseEmitter subscribe() {
    return subscribe(Duration.ofMillis(defaultSseTimeout));
  }

  @Override
  public SseEmitter subscribe(Duration timeout) {
    String lastId = getCurrentLastId();
    SseEmitter emitter = new SseEmitter(timeout.toMillis());
    SubscriberOutbox outbox = createOutbox(emitter, lastId);
    registerAndStart(outbox, emitter);
    return emitter;
  }

  @Override
  public SseEmitter resumeAfter(String lastEventId) {
    return resumeAfter(lastEventId, Duration.ofMillis(defaultSseTimeout));
  }

  @Override
  public SseEmitter resumeAfter(String lastEventId, Duration timeout) {
    SseEmitter emitter = new SseEmitter(timeout.toMillis());

    Range<String> range =
        Range.from(Range.Boundary.excluding(lastEventId), Range.Boundary.unbounded());
    List<StreamMessage<String, String>> missed = commands.xrange(streamKey, range);
    List<OdysseyEvent> replayEvents = missed.stream().map(this::toOdysseyEvent).toList();

    String lastId = replayEvents.isEmpty() ? lastEventId : replayEvents.getLast().id();
    SubscriberOutbox outbox = createOutbox(emitter, lastId);
    for (OdysseyEvent event : replayEvents) {
      outbox.enqueue(event);
    }
    registerAndStart(outbox, emitter);
    return emitter;
  }

  @Override
  public SseEmitter replayLast(int count) {
    return replayLast(count, Duration.ofMillis(defaultSseTimeout));
  }

  @Override
  public SseEmitter replayLast(int count, Duration timeout) {
    SseEmitter emitter = new SseEmitter(timeout.toMillis());

    int cappedCount = Math.min(count, maxLastN);
    List<StreamMessage<String, String>> messages =
        new ArrayList<>(
            commands.xrevrange(streamKey, Range.unbounded(), Limit.create(0, cappedCount)));
    Collections.reverse(messages);
    List<OdysseyEvent> replayEvents = messages.stream().map(this::toOdysseyEvent).toList();

    String lastId = replayEvents.isEmpty() ? getCurrentLastId() : replayEvents.getLast().id();
    SubscriberOutbox outbox = createOutbox(emitter, lastId);
    for (OdysseyEvent event : replayEvents) {
      outbox.enqueue(event);
    }
    registerAndStart(outbox, emitter);
    return emitter;
  }

  @Override
  public void close() {
    fanout.shutdown();
  }

  @Override
  public void delete() {
    fanout.shutdownImmediately();
    commands.del(streamKey);
  }

  @Override
  public String getStreamKey() {
    return streamKey;
  }

  private String getCurrentLastId() {
    List<StreamMessage<String, String>> latest =
        commands.xrevrange(streamKey, Range.unbounded(), Limit.create(0, 1));
    return latest.isEmpty() ? "0-0" : latest.getFirst().getId();
  }

  private Function<String, List<OdysseyEvent>> createReadFunction() {
    return lastId -> {
      List<StreamMessage<String, String>> messages =
          commands.xread(XReadArgs.Builder.count(100), StreamOffset.from(streamKey, lastId));
      if (messages == null || messages.isEmpty()) {
        return List.of();
      }
      return messages.stream().map(this::toOdysseyEvent).toList();
    };
  }

  private SubscriberOutbox createOutbox(SseEmitter emitter, String lastReadId) {
    return new SubscriberOutbox(
        createReadFunction(), emitter, streamKey, lastReadId, keepAliveInterval);
  }

  private void registerAndStart(SubscriberOutbox outbox, SseEmitter emitter) {
    fanout.addSubscriber(outbox);
    Runnable cleanup =
        () -> {
          fanout.removeSubscriber(outbox);
          outbox.closeImmediately();
        };
    emitter.onCompletion(cleanup);
    emitter.onError(e -> cleanup.run());
    emitter.onTimeout(cleanup);
    outbox.start();
  }

  private OdysseyEvent toOdysseyEvent(StreamMessage<String, String> message) {
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
