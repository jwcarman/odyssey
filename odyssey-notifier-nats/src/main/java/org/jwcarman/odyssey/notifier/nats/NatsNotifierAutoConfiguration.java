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
package org.jwcarman.odyssey.notifier.nats;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import java.io.IOException;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = OdysseyAutoConfiguration.class)
@ConditionalOnClass(Connection.class)
@EnableConfigurationProperties(NatsNotifierProperties.class)
public class NatsNotifierAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(Connection.class)
  public Connection natsConnection(NatsNotifierProperties properties)
      throws IOException, InterruptedException {
    Options options = new Options.Builder().server(properties.getUrl()).build();
    return Nats.connect(options);
  }

  @Bean
  public NatsOdysseyStreamNotifier natsOdysseyStreamNotifier(
      Connection connection, NatsNotifierProperties properties) {
    return new NatsOdysseyStreamNotifier(connection, properties.getSubjectPrefix());
  }
}
