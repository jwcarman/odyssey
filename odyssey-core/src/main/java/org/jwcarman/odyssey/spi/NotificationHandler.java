package org.jwcarman.odyssey.spi;

@FunctionalInterface
public interface NotificationHandler {

  void onNotification(String streamKey, String eventId);
}
