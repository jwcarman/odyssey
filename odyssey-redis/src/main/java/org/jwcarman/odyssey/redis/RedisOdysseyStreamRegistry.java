package org.jwcarman.odyssey.redis;

import io.lettuce.core.api.sync.RedisCommands;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;

class RedisOdysseyStreamRegistry implements OdysseyStreamRegistry {

  private final RedisCommands<String, String> commands;
  private final PubSubNotificationListener listener;
  private final String streamPrefix;
  private final long keepAliveInterval;
  private final long defaultSseTimeout;
  private final long maxLen;
  private final int maxLastN;
  private final long ephemeralTtlSeconds;
  private final long channelTtlSeconds;
  private final long broadcastTtlSeconds;

  private final ConcurrentMap<String, RedisOdysseyStream> cache = new ConcurrentHashMap<>();

  RedisOdysseyStreamRegistry(
      RedisCommands<String, String> commands,
      PubSubNotificationListener listener,
      String streamPrefix,
      long keepAliveInterval,
      long defaultSseTimeout,
      long maxLen,
      int maxLastN,
      long ephemeralTtlSeconds,
      long channelTtlSeconds,
      long broadcastTtlSeconds) {
    this.commands = commands;
    this.listener = listener;
    this.streamPrefix = streamPrefix;
    this.keepAliveInterval = keepAliveInterval;
    this.defaultSseTimeout = defaultSseTimeout;
    this.maxLen = maxLen;
    this.maxLastN = maxLastN;
    this.ephemeralTtlSeconds = ephemeralTtlSeconds;
    this.channelTtlSeconds = channelTtlSeconds;
    this.broadcastTtlSeconds = broadcastTtlSeconds;
  }

  @Override
  public OdysseyStream ephemeral() {
    String uuid = UUID.randomUUID().toString();
    String streamKey = streamPrefix + "ephemeral:" + uuid;
    return createStream(streamKey, ephemeralTtlSeconds);
  }

  @Override
  public OdysseyStream channel(String name) {
    String streamKey = streamPrefix + "channel:" + name;
    return cache.computeIfAbsent(streamKey, key -> createStream(key, channelTtlSeconds));
  }

  @Override
  public OdysseyStream broadcast(String name) {
    String streamKey = streamPrefix + "broadcast:" + name;
    return cache.computeIfAbsent(streamKey, key -> createStream(key, broadcastTtlSeconds));
  }

  private RedisOdysseyStream createStream(String streamKey, long ttlSeconds) {
    TopicFanout fanout = new TopicFanout();
    listener.registerFanout(streamKey, fanout);
    RedisOdysseyStream stream =
        new RedisOdysseyStream(
            streamKey,
            commands,
            fanout,
            keepAliveInterval,
            defaultSseTimeout,
            maxLen,
            maxLastN,
            streamPrefix,
            ttlSeconds);
    if (ttlSeconds > 0) {
      commands.expire(streamKey, ttlSeconds);
    }
    return stream;
  }
}
