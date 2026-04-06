package org.jwcarman.odyssey.notifier.postgresql;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odyssey.notifier.postgresql")
public class PostgresNotifierProperties {

  private String channel = "odyssey_notify";

  private int pollTimeoutMillis = 500;

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public int getPollTimeoutMillis() {
    return pollTimeoutMillis;
  }

  public void setPollTimeoutMillis(int pollTimeoutMillis) {
    this.pollTimeoutMillis = pollTimeoutMillis;
  }
}
