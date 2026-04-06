package org.jwcarman.odyssey.spi;

public interface OdysseyStreamNotifier {

  void notify(String streamKey, String eventId);

  void subscribe(String pattern, NotificationHandler handler);
}
