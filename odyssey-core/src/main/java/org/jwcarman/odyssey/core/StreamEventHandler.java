package org.jwcarman.odyssey.core;

public interface StreamEventHandler {

  void onEvent(OdysseyEvent event);

  void onKeepAlive();

  void onComplete();

  void onError(Exception e);
}
