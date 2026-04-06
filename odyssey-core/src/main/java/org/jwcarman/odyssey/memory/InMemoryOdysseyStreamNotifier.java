package org.jwcarman.odyssey.memory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jwcarman.odyssey.spi.NotificationHandler;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;

public class InMemoryOdysseyStreamNotifier implements OdysseyStreamNotifier {

  private final List<NotificationHandler> handlers = new CopyOnWriteArrayList<>();

  @Override
  public void notify(String streamKey, String eventId) {
    for (NotificationHandler handler : handlers) {
      handler.onNotification(streamKey, eventId);
    }
  }

  @Override
  public void subscribe(NotificationHandler handler) {
    handlers.add(handler);
  }
}
