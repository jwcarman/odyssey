package org.jwcarman.odyssey.notifier.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@AutoConfiguration
@ConditionalOnClass(RedisConnectionFactory.class)
@EnableConfigurationProperties(RedisNotifierProperties.class)
public class RedisNotifierAutoConfiguration {

  @Bean
  public RedisOdysseyStreamNotifier redisOdysseyStreamNotifier(
      RedisConnectionFactory connectionFactory, RedisNotifierProperties properties) {
    LettuceConnectionFactory lcf = (LettuceConnectionFactory) connectionFactory;
    RedisClient client = (RedisClient) lcf.getNativeClient();

    StatefulRedisPubSubConnection<String, String> pubSubConnection =
        client.connectPubSub(StringCodec.UTF8);
    io.lettuce.core.api.StatefulRedisConnection<String, String> sharedConnection =
        client.connect(StringCodec.UTF8);

    return new RedisOdysseyStreamNotifier(
        pubSubConnection, sharedConnection.sync(), properties.getChannelPrefix());
  }
}
