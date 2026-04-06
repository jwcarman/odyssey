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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class DefaultOdysseyStream implements OdysseyStream {

  private final String streamKey;
  private final OdysseyEventLog eventLog;
  private final OdysseyStreamNotifier notifier;
  private final StreamSubscriberGroup subscriberGroup;
  private final long keepAliveInterval;
  private final long defaultSseTimeout;
  private final int maxLastN;

  DefaultOdysseyStream(
      String streamKey,
      OdysseyEventLog eventLog,
      OdysseyStreamNotifier notifier,
      StreamSubscriberGroup subscriberGroup,
      long keepAliveInterval,
      long defaultSseTimeout,
      int maxLastN) {
    this.streamKey = streamKey;
    this.eventLog = eventLog;
    this.notifier = notifier;
    this.subscriberGroup = subscriberGroup;
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
    return createSubscription(lastId, timeout, List.of());
  }

  @Override
  public SseEmitter resumeAfter(String lastEventId) {
    return resumeAfter(lastEventId, Duration.ofMillis(defaultSseTimeout));
  }

  @Override
  public SseEmitter resumeAfter(String lastEventId, Duration timeout) {
    List<OdysseyEvent> replayEvents = eventLog.readAfter(streamKey, lastEventId).toList();
    String lastId = replayEvents.isEmpty() ? lastEventId : replayEvents.getLast().id();
    return createSubscription(lastId, timeout, replayEvents);
  }

  @Override
  public SseEmitter replayLast(int count) {
    return replayLast(count, Duration.ofMillis(defaultSseTimeout));
  }

  @Override
  public SseEmitter replayLast(int count, Duration timeout) {
    int cappedCount = Math.min(count, maxLastN);
    List<OdysseyEvent> replayEvents = eventLog.readLast(streamKey, cappedCount).toList();
    String lastId = replayEvents.isEmpty() ? getCurrentLastId() : replayEvents.getLast().id();
    return createSubscription(lastId, timeout, replayEvents);
  }

  @Override
  public void close() {
    subscriberGroup.shutdown();
  }

  @Override
  public void delete() {
    subscriberGroup.shutdownImmediately();
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

  private SseEmitter createSubscription(
      String lastId, Duration timeout, List<OdysseyEvent> replayEvents) {
    SseEmitter emitter = new SseEmitter(timeout.toMillis());
    AtomicReference<StreamSubscriber> subscriberRef = new AtomicReference<>();
    Runnable cleanup =
        () -> {
          StreamSubscriber s = subscriberRef.get();
          if (s != null) {
            subscriberGroup.removeSubscriber(s);
            s.closeImmediately();
          }
        };
    SseStreamEventHandler handler = new SseStreamEventHandler(emitter, cleanup);
    StreamSubscriber subscriber =
        new StreamSubscriber(eventLog, handler, streamKey, lastId, keepAliveInterval);
    subscriberRef.set(subscriber);
    subscriberGroup.addSubscriber(subscriber);
    try {
      for (OdysseyEvent event : replayEvents) {
        subscriber.enqueue(event);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    subscriber.start();
    return emitter;
  }
}
