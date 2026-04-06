package org.jwcarman.odyssey.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.engine.DefaultOdysseyStreamRegistry;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.memory.InMemoryOdysseyStreamNotifier;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class OdysseyAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OdysseyAutoConfiguration.class));

  @Test
  void createsPropertiesBean() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(OdysseyProperties.class);
        });
  }

  @Test
  void createsDefaultStreamRegistry() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(DefaultOdysseyStreamRegistry.class);
        });
  }

  @Test
  void createsInMemoryEventLogWhenNoOtherBeanExists() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(OdysseyEventLog.class);
          assertThat(context.getBean(OdysseyEventLog.class))
              .isInstanceOf(InMemoryOdysseyEventLog.class);
        });
  }

  @Test
  void createsInMemoryStreamNotifierWhenNoOtherBeanExists() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
          assertThat(context.getBean(OdysseyStreamNotifier.class))
              .isInstanceOf(InMemoryOdysseyStreamNotifier.class);
        });
  }

  @Test
  void doesNotCreateInMemoryEventLogWhenExternalBeanExists() {
    contextRunner
        .withUserConfiguration(CustomEventLogConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
              assertThat(context.getBean(OdysseyEventLog.class))
                  .isInstanceOf(StubOdysseyEventLog.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyEventLog.class);
            });
  }

  @Test
  void doesNotCreateInMemoryStreamNotifierWhenExternalBeanExists() {
    contextRunner
        .withUserConfiguration(CustomStreamNotifierConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyStreamNotifier.class);
              assertThat(context.getBean(OdysseyStreamNotifier.class))
                  .isInstanceOf(StubOdysseyStreamNotifier.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyStreamNotifier.class);
            });
  }

  @Test
  void registryIsCreatedEvenWithExternalBeans() {
    contextRunner
        .withUserConfiguration(
            CustomEventLogConfiguration.class, CustomStreamNotifierConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(DefaultOdysseyStreamRegistry.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomEventLogConfiguration {

    @Bean
    OdysseyEventLog customEventLog() {
      return new StubOdysseyEventLog();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomStreamNotifierConfiguration {

    @Bean
    OdysseyStreamNotifier customStreamNotifier() {
      return new StubOdysseyStreamNotifier();
    }
  }

  static class StubOdysseyEventLog implements OdysseyEventLog {

    @Override
    public String append(String streamKey, org.jwcarman.odyssey.core.OdysseyEvent event) {
      return "stub-id";
    }

    @Override
    public java.util.stream.Stream<org.jwcarman.odyssey.core.OdysseyEvent> readAfter(
        String streamKey, String lastId) {
      return java.util.stream.Stream.empty();
    }

    @Override
    public java.util.stream.Stream<org.jwcarman.odyssey.core.OdysseyEvent> readLast(
        String streamKey, int count) {
      return java.util.stream.Stream.empty();
    }

    @Override
    public void delete(String streamKey) {}
  }

  static class StubOdysseyStreamNotifier implements OdysseyStreamNotifier {

    @Override
    public void notify(String streamKey, String eventId) {}

    @Override
    public void subscribe(org.jwcarman.odyssey.spi.NotificationHandler handler) {}
  }
}
