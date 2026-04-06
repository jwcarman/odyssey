package org.jwcarman.odyssey.notifier.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyStreamNotifier;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class RedisNotifierAutoConfigurationTest {

  private static LettuceConnectionFactory createMockConnectionFactory() {
    LettuceConnectionFactory factory = mock(LettuceConnectionFactory.class);
    RedisClient client = mock(RedisClient.class);
    StatefulRedisConnection<String, String> connection = mock();
    RedisCommands<String, String> commands = mock();
    StatefulRedisPubSubConnection<String, String> pubSubConnection = mock();
    RedisPubSubCommands<String, String> pubSubCommands = mock();

    when(factory.getNativeClient()).thenReturn(client);
    when(client.connect(StringCodec.UTF8)).thenReturn(connection);
    when(connection.sync()).thenReturn(commands);
    when(client.connectPubSub(StringCodec.UTF8)).thenReturn(pubSubConnection);
    when(pubSubConnection.sync()).thenReturn(pubSubCommands);

    return factory;
  }

  @Test
  void createsRedisStreamNotifierBean() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisNotifierAutoConfiguration.class))
        .withUserConfiguration(MockRedisConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(RedisOdysseyStreamNotifier.class);
              assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
            });
  }

  @Test
  void redisNotifierSuppressesInMemoryFallback() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                RedisNotifierAutoConfiguration.class, OdysseyAutoConfiguration.class))
        .withUserConfiguration(MockRedisConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
              assertThat(context.getBean(OdysseyStreamNotifier.class))
                  .isInstanceOf(RedisOdysseyStreamNotifier.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyStreamNotifier.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class MockRedisConfiguration {

    @Bean
    RedisConnectionFactory redisConnectionFactory() {
      return createMockConnectionFactory();
    }
  }
}
