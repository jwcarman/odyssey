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
package org.jwcarman.odyssey.spi;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

public abstract class AbstractOdysseyEventLog implements OdysseyEventLog {

  private static final TimeBasedEpochGenerator UUID_GENERATOR =
      Generators.timeBasedEpochGenerator();

  private final String ephemeralPrefix;
  private final String channelPrefix;
  private final String broadcastPrefix;

  protected AbstractOdysseyEventLog(
      String ephemeralPrefix, String channelPrefix, String broadcastPrefix) {
    this.ephemeralPrefix = ephemeralPrefix;
    this.channelPrefix = channelPrefix;
    this.broadcastPrefix = broadcastPrefix;
  }

  @Override
  public String ephemeralKey() {
    return ephemeralPrefix + UUID.randomUUID();
  }

  @Override
  public String channelKey(String name) {
    return channelPrefix + name;
  }

  @Override
  public String broadcastKey(String name) {
    return broadcastPrefix + name;
  }

  protected String generateEventId() {
    return UUID_GENERATOR.generate().toString();
  }

  protected String ephemeralPrefix() {
    return ephemeralPrefix;
  }

  protected String channelPrefix() {
    return channelPrefix;
  }

  protected String broadcastPrefix() {
    return broadcastPrefix;
  }
}
