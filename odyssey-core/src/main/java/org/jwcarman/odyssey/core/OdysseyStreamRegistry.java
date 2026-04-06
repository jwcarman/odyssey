package org.jwcarman.odyssey.core;

public interface OdysseyStreamRegistry {

  OdysseyStream ephemeral();

  OdysseyStream channel(String name);

  OdysseyStream broadcast(String name);

  OdysseyStream stream(String streamKey);
}
