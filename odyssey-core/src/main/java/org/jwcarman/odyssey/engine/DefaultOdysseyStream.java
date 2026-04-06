package org.jwcarman.odyssey.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class DefaultOdysseyStream implements OdysseyStream {

  private final String streamKey;
  private final OdysseyEventLog eventLog;
  private final OdysseyStreamNotifier notifier;
  private final TopicFanout fanout;
  private final long keepAliveInterval;
  private final long defaultSseTimeout;
  private final int maxLastN;

  DefaultOdysseyStream(
      String streamKey,
      OdysseyEventLog eventLog,
      OdysseyStreamNotifier notifier,
      TopicFanout fanout,
      long keepAliveInterval,
      long defaultSseTimeout,
      int maxLastN) {
    this.streamKey = streamKey;
    this.eventLog = eventLog;
    this.notifier = notifier;
    this.fanout = fanout;
    this.keepAliveInterval = keepAliveInterval;
    this.defaultSseTimeout = defaultSseTimeout;
    this.maxLastN = maxLastN;
  }

  @Override
  public String publish(String eventType, String payload) {
    OdysseyEvent event =
        OdysseyEvent.builder()
            .streamKey(streamKey)
            .eventType(eventType)
            .payload(payload)
            .timestamp(Instant.now())
            .metadata(Map.of())
            .build();
    String entryId = eventLog.append(streamKey, event);
    notifier.notify(streamKey, entryId);
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

    List<OdysseyEvent> replayEvents = eventLog.readAfter(streamKey, lastEventId).toList();

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
    List<OdysseyEvent> replayEvents = eventLog.readLast(streamKey, cappedCount).toList();

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
    eventLog.delete(streamKey);
  }

  @Override
  public String getStreamKey() {
    return streamKey;
  }

  private String getCurrentLastId() {
    List<OdysseyEvent> latest = eventLog.readLast(streamKey, 1).toList();
    return latest.isEmpty() ? "0-0" : latest.getFirst().id();
  }

  private Function<String, List<OdysseyEvent>> createReadFunction() {
    return lastId -> eventLog.readAfter(streamKey, lastId).toList();
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
}
