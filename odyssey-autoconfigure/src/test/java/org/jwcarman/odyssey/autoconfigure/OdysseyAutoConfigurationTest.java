package org.jwcarman.odyssey.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.odyssey.redis.PubSubNotificationListener;
import org.jwcarman.odyssey.redis.RedisOdysseyStreamRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class OdysseyAutoConfigurationTest {

  private static LettuceConnectionFactory createMockConnectionFactory() {
    LettuceConnectionFactory factory = mock(LettuceConnectionFactory.class);
    RedisClient client = mock(RedisClient.class);
    when(factory.getRequiredNativeClient()).thenReturn(client);

    StatefulRedisPubSubConnection<String, String> pubSubConnection = mock();
    RedisPubSubCommands<String, String> pubSubCommands = mock();
    when(pubSubConnection.sync()).thenReturn(pubSubCommands);
    when(client.connectPubSub()).thenReturn(pubSubConnection);

    StatefulRedisConnection<String, String> redisConnection = mock();
    RedisCommands<String, String> commands = mock();
    when(redisConnection.sync()).thenReturn(commands);
    when(client.connect()).thenReturn(redisConnection);

    return factory;
  }

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OdysseyAutoConfiguration.class))
          .withBean(
              LettuceConnectionFactory.class,
              OdysseyAutoConfigurationTest::createMockConnectionFactory);

  @Test
  void createsAllRequiredBeans() {
    contextRunner.run(
        context -> {
          assertNotNull(context.getBean("odysseyPubSubConnection"));
          assertNotNull(context.getBean("odysseyRedisConnection"));
          assertNotNull(context.getBean(PubSubNotificationListener.class));
          assertNotNull(context.getBean(OdysseyStreamRegistry.class));
          assertNotNull(context.getBean("odysseyPubSubLifecycle", SmartLifecycle.class));
        });
  }

  @Test
  void registryIsBoundToInterface() {
    contextRunner.run(
        context -> {
          OdysseyStreamRegistry registry = context.getBean(OdysseyStreamRegistry.class);
          assertTrue(registry instanceof RedisOdysseyStreamRegistry);
        });
  }

  @Test
  void twoDistinctConnectionsAreCreated() {
    contextRunner.run(
        context -> {
          Object pubSub = context.getBean("odysseyPubSubConnection");
          Object shared = context.getBean("odysseyRedisConnection");
          assertNotSame(pubSub, shared);
        });
  }

  @Test
  void notLoadedWithoutRedisConnectionFactory() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OdysseyAutoConfiguration.class))
        .run(
            context -> {
              assertFalse(context.containsBean("odysseyStreamRegistry"));
            });
  }

  @Test
  void pubSubLifecycleStartsAndStops() {
    contextRunner.run(
        context -> {
          SmartLifecycle lifecycle =
              context.getBean("odysseyPubSubLifecycle", SmartLifecycle.class);
          assertTrue(lifecycle.isRunning());
          lifecycle.stop();
          assertFalse(lifecycle.isRunning());
          lifecycle.start();
          assertTrue(lifecycle.isRunning());
        });
  }
}
