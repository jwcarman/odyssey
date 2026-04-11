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
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.substrate.journal.JournalFactory;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class OdysseyAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OdysseyAutoConfiguration.class))
          .withBean(JournalFactory.class, () -> Mockito.mock(JournalFactory.class))
          .withBean(ObjectMapper.class, ObjectMapper::new);

  @Test
  void createsOdysseyBean() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(Odyssey.class);
        });
  }

  @Test
  void odysseyBeanCanBeOverridden() {
    Odyssey custom = Mockito.mock(Odyssey.class);
    contextRunner
        .withBean(Odyssey.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(Odyssey.class);
              assertThat(context.getBean(Odyssey.class)).isSameAs(custom);
            });
  }
}
