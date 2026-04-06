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

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odyssey")
public class OdysseyProperties {

  private Duration keepAliveInterval = Duration.ofSeconds(30);
  private Duration sseTimeout = Duration.ZERO;
  private int maxLastN = 500;

  public Duration getKeepAliveInterval() {
    return keepAliveInterval;
  }

  public void setKeepAliveInterval(Duration keepAliveInterval) {
    this.keepAliveInterval = keepAliveInterval;
  }

  public Duration getSseTimeout() {
    return sseTimeout;
  }

  public void setSseTimeout(Duration sseTimeout) {
    this.sseTimeout = sseTimeout;
  }

  public int getMaxLastN() {
    return maxLastN;
  }

  public void setMaxLastN(int maxLastN) {
    this.maxLastN = maxLastN;
  }
}
