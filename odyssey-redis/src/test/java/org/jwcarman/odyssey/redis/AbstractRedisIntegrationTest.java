package org.jwcarman.odyssey.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
abstract class AbstractRedisIntegrationTest {

  private static final String STREAM_PREFIX = "odyssey:";
  private static final long DEFAULT_SSE_TIMEOUT = 0;
  private static final long MAX_LEN = 100_000;
  private static final int MAX_LAST_N = 500;
  private static final long EPHEMERAL_TTL = 300;
  private static final long CHANNEL_TTL = 3600;
  private static final long BROADCAST_TTL = 86400;

  @Container
  static final GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

  static RedisClient redisClient;
  static StatefulRedisConnection<String, String> connection;
  static StatefulRedisPubSubConnection<String, String> pubSubConnection;

  RedisCommands<String, String> commands;
  PubSubNotificationListener listener;
  RedisOdysseyStreamRegistry registry;

  @BeforeAll
  static void startRedis() {
    RedisURI uri =
        RedisURI.builder().withHost(redis.getHost()).withPort(redis.getMappedPort(6379)).build();
    redisClient = RedisClient.create(uri);
    connection = redisClient.connect();
    pubSubConnection = redisClient.connectPubSub();
  }

  @AfterAll
  static void stopRedis() {
    if (pubSubConnection != null) pubSubConnection.close();
    if (connection != null) connection.close();
    if (redisClient != null) redisClient.shutdown();
  }

  @BeforeEach
  void setUp() {
    commands = connection.sync();
    commands.flushall();

    listener = new PubSubNotificationListener(pubSubConnection, STREAM_PREFIX);
    listener.start();

    registry = createRegistry(30_000);
  }

  RedisOdysseyStreamRegistry createRegistry(long keepAliveInterval) {
    return new RedisOdysseyStreamRegistry(
        commands,
        listener,
        STREAM_PREFIX,
        keepAliveInterval,
        DEFAULT_SSE_TIMEOUT,
        MAX_LEN,
        MAX_LAST_N,
        EPHEMERAL_TTL,
        CHANNEL_TTL,
        BROADCAST_TTL);
  }

  @AfterEach
  void tearDown() {
    if (listener != null) {
      listener.stop();
    }
  }
}
