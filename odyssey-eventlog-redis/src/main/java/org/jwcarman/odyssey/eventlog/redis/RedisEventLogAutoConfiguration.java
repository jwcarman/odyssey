package org.jwcarman.odyssey.eventlog.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@AutoConfiguration
@ConditionalOnClass(RedisConnectionFactory.class)
@EnableConfigurationProperties(RedisEventLogProperties.class)
public class RedisEventLogAutoConfiguration {

  @Bean
  public RedisOdysseyEventLog redisOdysseyEventLog(
      RedisConnectionFactory connectionFactory, RedisEventLogProperties properties) {
    LettuceConnectionFactory lcf = (LettuceConnectionFactory) connectionFactory;
    RedisClient client = (RedisClient) lcf.getNativeClient();
    StatefulRedisConnection<String, String> connection = client.connect(StringCodec.UTF8);

    return new RedisOdysseyEventLog(
        connection.sync(),
        properties.getMaxLen(),
        properties.getStreamPrefix(),
        properties.getEphemeralTtl().toSeconds(),
        properties.getChannelTtl().toSeconds(),
        properties.getBroadcastTtl().toSeconds());
  }
}
