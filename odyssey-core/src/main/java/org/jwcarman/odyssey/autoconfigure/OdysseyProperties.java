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
