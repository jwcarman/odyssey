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
package org.jwcarman.odyssey.eventlog.dynamodb;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odyssey.eventlog.dynamodb")
public class DynamoDbEventLogProperties {

  private String ephemeralPrefix = "ephemeral:";
  private String channelPrefix = "channel:";
  private String broadcastPrefix = "broadcast:";
  private String tableName = "odyssey_events";
  private boolean autoCreateTable = false;
  private long ttlSeconds = 86400;

  public String getEphemeralPrefix() {
    return ephemeralPrefix;
  }

  public void setEphemeralPrefix(String ephemeralPrefix) {
    this.ephemeralPrefix = ephemeralPrefix;
  }

  public String getChannelPrefix() {
    return channelPrefix;
  }

  public void setChannelPrefix(String channelPrefix) {
    this.channelPrefix = channelPrefix;
  }

  public String getBroadcastPrefix() {
    return broadcastPrefix;
  }

  public void setBroadcastPrefix(String broadcastPrefix) {
    this.broadcastPrefix = broadcastPrefix;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public boolean isAutoCreateTable() {
    return autoCreateTable;
  }

  public void setAutoCreateTable(boolean autoCreateTable) {
    this.autoCreateTable = autoCreateTable;
  }

  public long getTtlSeconds() {
    return ttlSeconds;
  }

  public void setTtlSeconds(long ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }
}
