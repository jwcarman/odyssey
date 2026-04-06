/*
 * Copyright © 2026 James Carman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwcarman.odyssey.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.engine.DefaultOdysseyStreamRegistry;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.memory.InMemoryOdysseyStreamNotifier;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class OdysseyAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OdysseyAutoConfiguration.class))
          .withBean(ObjectMapper.class, ObjectMapper::new);

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

  @Test
  void logsWarningsWhenFallingBackToInMemoryImplementations(CapturedOutput output) {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(InMemoryOdysseyEventLog.class);
          assertThat(context).hasSingleBean(InMemoryOdysseyStreamNotifier.class);
        });
    assertThat(output)
        .contains("No OdysseyEventLog bean found; falling back to in-memory implementation")
        .contains("No OdysseyStreamNotifier bean found; falling back to in-memory implementation");
  }

  @Test
  void doesNotLogWarningsWhenExternalBeansExist(CapturedOutput output) {
    contextRunner
        .withUserConfiguration(
            CustomEventLogConfiguration.class, CustomStreamNotifierConfiguration.class)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(InMemoryOdysseyEventLog.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyStreamNotifier.class);
            });
    assertThat(output)
        .doesNotContain("No OdysseyEventLog bean found; falling back to in-memory implementation")
        .doesNotContain(
            "No OdysseyStreamNotifier bean found; falling back to in-memory implementation");
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
    public String ephemeralKey() {
      return "ephemeral:stub";
    }

    @Override
    public String channelKey(String name) {
      return "channel:" + name;
    }

    @Override
    public String broadcastKey(String name) {
      return "broadcast:" + name;
    }

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
