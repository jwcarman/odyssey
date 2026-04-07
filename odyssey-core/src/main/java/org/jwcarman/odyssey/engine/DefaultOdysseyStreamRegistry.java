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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import tools.jackson.databind.ObjectMapper;

/**
 * Default implementation of {@link OdysseyStreamRegistry} that coordinates an {@link
 * OdysseyEventLog} and {@link OdysseyStreamNotifier} to provide stream lifecycle management.
 */
public class DefaultOdysseyStreamRegistry implements OdysseyStreamRegistry {

  private final OdysseyEventLog eventLog;
  private final OdysseyStreamNotifier notifier;
  private final long keepAliveInterval;
  private final long defaultSseTimeout;
  private final int maxLastN;
  private final ObjectMapper objectMapper;

  private final ConcurrentMap<String, DefaultOdysseyStream> cache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, StreamSubscriberGroup> subscriberGroups =
      new ConcurrentHashMap<>();

  /**
   * Creates a new registry.
   *
   * @param eventLog the event log for storing and retrieving events
   * @param notifier the notifier for cross-node event notifications
   * @param keepAliveInterval the keep-alive interval in milliseconds
   * @param defaultSseTimeout the default SSE emitter timeout in milliseconds
   * @param maxLastN the maximum number of events for replay-last operations
   * @param objectMapper the Jackson object mapper for JSON serialization
   */
  public DefaultOdysseyStreamRegistry(
      OdysseyEventLog eventLog,
      OdysseyStreamNotifier notifier,
      long keepAliveInterval,
      long defaultSseTimeout,
      int maxLastN,
      ObjectMapper objectMapper) {
    this.eventLog = eventLog;
    this.notifier = notifier;
    this.keepAliveInterval = keepAliveInterval;
    this.defaultSseTimeout = defaultSseTimeout;
    this.maxLastN = maxLastN;
    this.objectMapper = objectMapper;

    notifier.subscribe(
        (streamKey, eventId) -> {
          StreamSubscriberGroup group = subscriberGroups.get(streamKey);
          if (group != null && group.hasSubscribers()) {
            group.nudgeAll();
          }
        });
  }

  @Override
  public OdysseyStream ephemeral() {
    String streamKey = eventLog.ephemeralKey();
    return createStream(streamKey);
  }

  @Override
  public OdysseyStream channel(String name) {
    String streamKey = eventLog.channelKey(name);
    return cache.computeIfAbsent(streamKey, this::createStream);
  }

  @Override
  public OdysseyStream broadcast(String name) {
    String streamKey = eventLog.broadcastKey(name);
    return cache.computeIfAbsent(streamKey, this::createStream);
  }

  @Override
  public OdysseyStream stream(String streamKey) {
    return cache.computeIfAbsent(streamKey, this::createStream);
  }

  private DefaultOdysseyStream createStream(String streamKey) {
    StreamSubscriberGroup group = new StreamSubscriberGroup();
    subscriberGroups.put(streamKey, group);
    return new DefaultOdysseyStream(
        streamKey,
        eventLog,
        notifier,
        group,
        new DefaultOdysseyStream.StreamConfig(keepAliveInterval, defaultSseTimeout, maxLastN),
        objectMapper);
  }
}
