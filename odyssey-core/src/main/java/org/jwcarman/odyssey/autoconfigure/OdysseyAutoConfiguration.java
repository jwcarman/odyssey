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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jwcarman.odyssey.engine.DefaultOdysseyStreamRegistry;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.memory.InMemoryOdysseyStreamNotifier;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Boot auto-configuration for OdySSEy. Wires up the {@link
 * org.jwcarman.odyssey.engine.DefaultOdysseyStreamRegistry} with the available {@link
 * OdysseyEventLog} and {@link OdysseyStreamNotifier} beans.
 *
 * <p>When no backend-specific event log or notifier bean is present on the classpath, in-memory
 * fallback implementations are created automatically with a warning log. This is suitable for
 * single-node environments and testing, but clustered deployments should add a backend module
 * (e.g., {@code odyssey-eventlog-redis} and {@code odyssey-notifier-redis}).
 *
 * <p>Default property values are loaded from {@code odyssey-defaults.properties}.
 */
@AutoConfiguration
@EnableConfigurationProperties(OdysseyProperties.class)
@PropertySource("classpath:odyssey-defaults.properties")
public class OdysseyAutoConfiguration {

  /** Creates a new auto-configuration instance. */
  public OdysseyAutoConfiguration() {}

  private static final Log log = LogFactory.getLog(OdysseyAutoConfiguration.class);

  /**
   * Creates an in-memory event log when no {@link OdysseyEventLog} bean is found. Logs a warning
   * because the in-memory implementation does not survive restarts or support clustering.
   *
   * @return an in-memory event log
   */
  @Bean
  @ConditionalOnMissingBean(OdysseyEventLog.class)
  public InMemoryOdysseyEventLog odysseyEventLog() {
    log.warn(
        "No OdysseyEventLog bean found; falling back to in-memory implementation. "
            + "Suitable for single-node environments and testing only. "
            + "For clustered deployments, add a backend module (e.g. odyssey-eventlog-redis).");
    return new InMemoryOdysseyEventLog();
  }

  /**
   * Creates an in-memory stream notifier when no {@link OdysseyStreamNotifier} bean is found. Logs
   * a warning because the in-memory implementation only notifies subscribers within the same JVM.
   *
   * @return an in-memory stream notifier
   */
  @Bean
  @ConditionalOnMissingBean(OdysseyStreamNotifier.class)
  public InMemoryOdysseyStreamNotifier odysseyStreamNotifier() {
    log.warn(
        "No OdysseyStreamNotifier bean found; falling back to in-memory implementation. "
            + "Suitable for single-node environments and testing only. "
            + "For clustered deployments, add a backend module (e.g. odyssey-notifier-redis).");
    return new InMemoryOdysseyStreamNotifier();
  }

  /**
   * Creates the stream registry that wires together the event log, notifier, and configuration
   * properties.
   *
   * @param eventLog the event log implementation
   * @param notifier the stream notifier implementation
   * @param properties the OdySSEy configuration properties
   * @param objectMapper the Jackson object mapper for JSON serialization
   * @return the configured stream registry
   */
  @Bean
  public DefaultOdysseyStreamRegistry odysseyStreamRegistry(
      OdysseyEventLog eventLog,
      OdysseyStreamNotifier notifier,
      OdysseyProperties properties,
      ObjectMapper objectMapper) {
    return new DefaultOdysseyStreamRegistry(
        eventLog,
        notifier,
        properties.keepAliveInterval().toMillis(),
        properties.sseTimeout().toMillis(),
        properties.maxLastN(),
        objectMapper);
  }
}
