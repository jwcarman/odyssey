package org.jwcarman.odyssey.engine;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;

public class DefaultOdysseyStreamRegistry implements OdysseyStreamRegistry {

  private final OdysseyEventLog eventLog;
  private final OdysseyStreamNotifier notifier;
  private final String streamPrefix;
  private final long keepAliveInterval;
  private final long defaultSseTimeout;
  private final int maxLastN;

  private final ConcurrentMap<String, DefaultOdysseyStream> cache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, TopicFanout> fanouts = new ConcurrentHashMap<>();

  public DefaultOdysseyStreamRegistry(
      OdysseyEventLog eventLog,
      OdysseyStreamNotifier notifier,
      String streamPrefix,
      long keepAliveInterval,
      long defaultSseTimeout,
      int maxLastN) {
    this.eventLog = eventLog;
    this.notifier = notifier;
    this.streamPrefix = streamPrefix;
    this.keepAliveInterval = keepAliveInterval;
    this.defaultSseTimeout = defaultSseTimeout;
    this.maxLastN = maxLastN;

    notifier.subscribe(
        "*",
        (streamKey, eventId) -> {
          TopicFanout fanout = fanouts.get(streamKey);
          if (fanout != null && fanout.hasSubscribers()) {
            fanout.nudgeAll();
          }
        });
  }

  @Override
  public OdysseyStream ephemeral() {
    String uuid = UUID.randomUUID().toString();
    String streamKey = streamPrefix + "ephemeral:" + uuid;
    return createStream(streamKey);
  }

  @Override
  public OdysseyStream channel(String name) {
    String streamKey = streamPrefix + "channel:" + name;
    return cache.computeIfAbsent(streamKey, this::createStream);
  }

  @Override
  public OdysseyStream broadcast(String name) {
    String streamKey = streamPrefix + "broadcast:" + name;
    return cache.computeIfAbsent(streamKey, this::createStream);
  }

  private DefaultOdysseyStream createStream(String streamKey) {
    TopicFanout fanout = new TopicFanout();
    fanouts.put(streamKey, fanout);
    return new DefaultOdysseyStream(
        streamKey, eventLog, notifier, fanout, keepAliveInterval, defaultSseTimeout, maxLastN);
  }
}
