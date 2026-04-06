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

@AutoConfiguration
@EnableConfigurationProperties(OdysseyProperties.class)
@PropertySource("classpath:odyssey-defaults.properties")
public class OdysseyAutoConfiguration {

  private static final Log log = LogFactory.getLog(OdysseyAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean(OdysseyEventLog.class)
  public InMemoryOdysseyEventLog odysseyEventLog() {
    log.warn(
        "No OdysseyEventLog bean found; falling back to in-memory implementation. "
            + "Suitable for single-node environments and testing only. "
            + "For clustered deployments, add a backend module (e.g. odyssey-eventlog-redis).");
    return new InMemoryOdysseyEventLog();
  }

  @Bean
  @ConditionalOnMissingBean(OdysseyStreamNotifier.class)
  public InMemoryOdysseyStreamNotifier odysseyStreamNotifier() {
    log.warn(
        "No OdysseyStreamNotifier bean found; falling back to in-memory implementation. "
            + "Suitable for single-node environments and testing only. "
            + "For clustered deployments, add a backend module (e.g. odyssey-notifier-redis).");
    return new InMemoryOdysseyStreamNotifier();
  }

  @Bean
  public DefaultOdysseyStreamRegistry odysseyStreamRegistry(
      OdysseyEventLog eventLog, OdysseyStreamNotifier notifier, OdysseyProperties properties) {
    return new DefaultOdysseyStreamRegistry(
        eventLog,
        notifier,
        properties.keepAliveInterval().toMillis(),
        properties.sseTimeout().toMillis(),
        properties.maxLastN());
  }
}
