package org.jwcarman.odyssey.spi;

import java.util.UUID;

public abstract class AbstractOdysseyEventLog implements OdysseyEventLog {

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
