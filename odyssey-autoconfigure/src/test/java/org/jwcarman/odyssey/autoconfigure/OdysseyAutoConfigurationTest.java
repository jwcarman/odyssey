package org.jwcarman.odyssey.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OdysseyAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OdysseyAutoConfiguration.class));

  @Test
  void createsPropertiesBean() {
    contextRunner.run(
        context -> {
          assertNotNull(context.getBean(OdysseyProperties.class));
        });
  }
}
