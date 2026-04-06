package org.jwcarman.odyssey.eventlog.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import org.jwcarman.odyssey.autoconfigure.OdysseyProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@AutoConfiguration
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisEventLogAutoConfiguration {

  @Bean
  public RedisOdysseyEventLog redisOdysseyEventLog(
      RedisConnectionFactory connectionFactory, OdysseyProperties properties) {
    LettuceConnectionFactory lcf = (LettuceConnectionFactory) connectionFactory;
    RedisClient client = (RedisClient) lcf.getNativeClient();
    StatefulRedisConnection<String, String> connection = client.connect(StringCodec.UTF8);

    OdysseyProperties.Redis redis = properties.getRedis();
    OdysseyProperties.Redis.Ttl ttl = redis.getTtl();
    return new RedisOdysseyEventLog(
        connection.sync(),
        redis.getMaxLen(),
        redis.getStreamPrefix(),
        ttl.getEphemeral().toSeconds(),
        ttl.getChannel().toSeconds(),
        ttl.getBroadcast().toSeconds());
  }
}
