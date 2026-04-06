package org.jwcarman.odyssey.autoconfigure;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.odyssey.redis.PubSubNotificationListener;
import org.jwcarman.odyssey.redis.RedisOdysseyStreamRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@AutoConfiguration
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnBean(RedisConnectionFactory.class)
@EnableConfigurationProperties(OdysseyProperties.class)
public class OdysseyAutoConfiguration {

  @Bean
  @Qualifier("odysseyPubSub")
  public StatefulRedisPubSubConnection<String, String> odysseyPubSubConnection(
      RedisConnectionFactory connectionFactory) {
    RedisClient client = extractRedisClient(connectionFactory);
    return client.connectPubSub();
  }

  @Bean
  @Qualifier("odysseyShared")
  public StatefulRedisConnection<String, String> odysseyRedisConnection(
      RedisConnectionFactory connectionFactory) {
    RedisClient client = extractRedisClient(connectionFactory);
    return client.connect();
  }

  @Bean
  public PubSubNotificationListener odysseyPubSubNotificationListener(
      @Qualifier("odysseyPubSub")
          StatefulRedisPubSubConnection<String, String> odysseyPubSubConnection,
      OdysseyProperties properties) {
    return new PubSubNotificationListener(
        odysseyPubSubConnection, properties.getRedis().getStreamPrefix());
  }

  @Bean
  public SmartLifecycle odysseyPubSubLifecycle(
      PubSubNotificationListener odysseyPubSubNotificationListener) {
    return new SmartLifecycle() {
      private volatile boolean running;

      @Override
      public void start() {
        odysseyPubSubNotificationListener.start();
        running = true;
      }

      @Override
      public void stop() {
        odysseyPubSubNotificationListener.stop();
        running = false;
      }

      @Override
      public boolean isRunning() {
        return running;
      }
    };
  }

  @Bean
  public OdysseyStreamRegistry odysseyStreamRegistry(
      @Qualifier("odysseyShared") StatefulRedisConnection<String, String> odysseyRedisConnection,
      PubSubNotificationListener odysseyPubSubNotificationListener,
      OdysseyProperties properties) {
    OdysseyProperties.Redis redis = properties.getRedis();
    OdysseyProperties.Redis.Ttl ttl = redis.getTtl();
    return new RedisOdysseyStreamRegistry(
        odysseyRedisConnection.sync(),
        odysseyPubSubNotificationListener,
        redis.getStreamPrefix(),
        properties.getKeepAliveInterval().toMillis(),
        properties.getSseTimeout().toMillis(),
        redis.getMaxLen(),
        redis.getMaxLastN(),
        ttl.getEphemeral().toSeconds(),
        ttl.getChannel().toSeconds(),
        ttl.getBroadcast().toSeconds());
  }

  private static RedisClient extractRedisClient(RedisConnectionFactory connectionFactory) {
    if (!(connectionFactory instanceof LettuceConnectionFactory lettuceFactory)) {
      throw new IllegalStateException(
          "OdySSEy requires a LettuceConnectionFactory but found: "
              + connectionFactory.getClass().getName());
    }
    if (!(lettuceFactory.getRequiredNativeClient() instanceof RedisClient redisClient)) {
      throw new IllegalStateException(
          "OdySSEy requires a standalone RedisClient (not RedisClusterClient)");
    }
    return redisClient;
  }
}
