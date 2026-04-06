package org.jwcarman.odyssey.notifier.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odyssey.notifier.redis")
public class RedisNotifierProperties {

  private String channelPrefix = "odyssey:notify:";

  public String getChannelPrefix() {
    return channelPrefix;
  }

  public void setChannelPrefix(String channelPrefix) {
    this.channelPrefix = channelPrefix;
  }
}
