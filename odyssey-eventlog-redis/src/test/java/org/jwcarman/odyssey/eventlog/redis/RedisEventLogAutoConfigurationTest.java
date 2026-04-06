package org.jwcarman.odyssey.eventlog.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class RedisEventLogAutoConfigurationTest {

  private static LettuceConnectionFactory createMockConnectionFactory() {
    LettuceConnectionFactory factory = mock(LettuceConnectionFactory.class);
    RedisClient client = mock(RedisClient.class);
    StatefulRedisConnection<String, String> connection = mock();
    RedisCommands<String, String> commands = mock();

    when(factory.getNativeClient()).thenReturn(client);
    when(client.connect(StringCodec.UTF8)).thenReturn(connection);
    when(connection.sync()).thenReturn(commands);

    return factory;
  }

  @Test
  void createsRedisEventLogBean() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisEventLogAutoConfiguration.class))
        .withUserConfiguration(MockRedisConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(RedisOdysseyEventLog.class);
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
            });
  }

  @Test
  void redisEventLogSuppressesInMemoryFallback() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                RedisEventLogAutoConfiguration.class, OdysseyAutoConfiguration.class))
        .withUserConfiguration(MockRedisConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
              assertThat(context.getBean(OdysseyEventLog.class))
                  .isInstanceOf(RedisOdysseyEventLog.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyEventLog.class);
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
