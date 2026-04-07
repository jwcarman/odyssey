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

import org.jwcarman.odyssey.engine.DefaultOdysseyStreamRegistry;
import org.jwcarman.substrate.core.JournalFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Boot auto-configuration for OdySSEy. Wires up the {@link DefaultOdysseyStreamRegistry}
 * with a {@link JournalFactory} provided by Substrate's auto-configuration.
 *
 * <p>Default property values are loaded from {@code odyssey-defaults.properties}.
 */
@AutoConfiguration
@EnableConfigurationProperties(OdysseyProperties.class)
@PropertySource("classpath:odyssey-defaults.properties")
public class OdysseyAutoConfiguration {

  /** Creates a new auto-configuration instance. */
  public OdysseyAutoConfiguration() {
    // no-op
  }

  /**
   * Creates the stream registry that wires together the journal factory, object mapper, and
   * configuration properties.
   *
   * @param journalFactory the Substrate journal factory
   * @param objectMapper the Jackson object mapper for JSON serialization
   * @param properties the OdySSEy configuration properties
   * @return the configured stream registry
   */
  @Bean
  public DefaultOdysseyStreamRegistry odysseyStreamRegistry(
      JournalFactory journalFactory, ObjectMapper objectMapper, OdysseyProperties properties) {
    return new DefaultOdysseyStreamRegistry(
        journalFactory,
        objectMapper,
        properties.keepAliveInterval().toMillis(),
        properties.sseTimeout().toMillis());
  }
}
