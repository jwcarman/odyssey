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

import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.PublisherCustomizer;
import org.jwcarman.odyssey.core.SubscriberCustomizer;
import org.jwcarman.odyssey.engine.DefaultOdyssey;
import org.jwcarman.substrate.journal.JournalFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Boot auto-configuration for Odyssey. Triggered by the presence of {@code odyssey-core} on
 * the classpath plus a {@link JournalFactory} bean (usually contributed by one of the backend
 * starter modules or Substrate's own auto-configuration).
 *
 * <p>Creates a single {@link Odyssey} bean wired with the configured {@link JournalFactory},
 * Jackson's {@code ObjectMapper}, {@link OdysseyProperties}, and any {@link PublisherCustomizer} /
 * {@link SubscriberCustomizer} beans found in the application context.
 *
 * <p>The {@link #odyssey(JournalFactory, ObjectMapper, OdysseyProperties, ObjectProvider,
 * ObjectProvider)} factory method is annotated with {@code @ConditionalOnMissingBean}, so
 * applications that declare their own {@code Odyssey} bean take precedence.
 */
@AutoConfiguration
@EnableConfigurationProperties(OdysseyProperties.class)
@PropertySource("classpath:odyssey-defaults.properties")
public class OdysseyAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Odyssey odyssey(
      JournalFactory journalFactory,
      ObjectMapper objectMapper,
      OdysseyProperties properties,
      ObjectProvider<PublisherCustomizer> publisherCustomizers,
      ObjectProvider<SubscriberCustomizer> subscriberCustomizers) {
    return new DefaultOdyssey(
        journalFactory,
        objectMapper,
        properties,
        publisherCustomizers.orderedStream().toList(),
        subscriberCustomizers.orderedStream().toList());
  }
}
