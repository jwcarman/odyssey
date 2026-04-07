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
package org.jwcarman.odyssey.eventlog.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.RingbufferConfig;
import com.hazelcast.core.HazelcastInstance;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(before = OdysseyAutoConfiguration.class)
@ConditionalOnClass(HazelcastInstance.class)
@EnableConfigurationProperties(HazelcastEventLogProperties.class)
@PropertySource("classpath:odyssey-eventlog-hazelcast-defaults.properties")
public class HazelcastEventLogAutoConfiguration {

  @Bean
  public HazelcastOdysseyEventLog hazelcastOdysseyEventLog(
      HazelcastInstance hazelcastInstance,
      ObjectMapper objectMapper,
      HazelcastEventLogProperties properties) {
    configureRingbufferDefaults(hazelcastInstance, properties);
    return new HazelcastOdysseyEventLog(
        hazelcastInstance,
        objectMapper,
        properties.ringbufferCapacity(),
        properties.ephemeralPrefix(),
        properties.channelPrefix(),
        properties.broadcastPrefix());
  }

  private void configureRingbufferDefaults(
      HazelcastInstance hazelcastInstance, HazelcastEventLogProperties properties) {
    Config config = hazelcastInstance.getConfig();
    RingbufferConfig ringbufferConfig = new RingbufferConfig("odyssey:*");
    ringbufferConfig.setCapacity(properties.ringbufferCapacity());
    ringbufferConfig.setTimeToLiveSeconds((int) properties.ringbufferTtl().toSeconds());
    config.addRingBufferConfig(ringbufferConfig);
  }
}
