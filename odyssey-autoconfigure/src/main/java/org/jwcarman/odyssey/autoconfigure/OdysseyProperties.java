package org.jwcarman.odyssey.autoconfigure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odyssey")
public class OdysseyProperties {

  private Duration keepAliveInterval = Duration.ofSeconds(30);
  private Duration sseTimeout = Duration.ZERO;
  private Redis redis = new Redis();

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

  public Redis getRedis() {
    return redis;
  }

  public void setRedis(Redis redis) {
    this.redis = redis;
  }

  public static class Redis {

    private String streamPrefix = "odyssey:";
    private long maxLen = 100_000;
    private int maxLastN = 500;
    private Ttl ttl = new Ttl();

    public String getStreamPrefix() {
      return streamPrefix;
    }

    public void setStreamPrefix(String streamPrefix) {
      this.streamPrefix = streamPrefix;
    }

    public long getMaxLen() {
      return maxLen;
    }

    public void setMaxLen(long maxLen) {
      this.maxLen = maxLen;
    }

    public int getMaxLastN() {
      return maxLastN;
    }

    public void setMaxLastN(int maxLastN) {
      this.maxLastN = maxLastN;
    }

    public Ttl getTtl() {
      return ttl;
    }

    public void setTtl(Ttl ttl) {
      this.ttl = ttl;
    }

    public static class Ttl {

      private Duration ephemeral = Duration.ofMinutes(5);
      private Duration channel = Duration.ofHours(1);
      private Duration broadcast = Duration.ofHours(24);

      public Duration getEphemeral() {
        return ephemeral;
      }

      public void setEphemeral(Duration ephemeral) {
        this.ephemeral = ephemeral;
      }

      public Duration getChannel() {
        return channel;
      }

      public void setChannel(Duration channel) {
        this.channel = channel;
      }

      public Duration getBroadcast() {
        return broadcast;
      }

      public void setBroadcast(Duration broadcast) {
        this.broadcast = broadcast;
      }
    }
  }
}
