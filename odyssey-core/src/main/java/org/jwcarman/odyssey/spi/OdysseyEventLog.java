package org.jwcarman.odyssey.spi;

import java.util.stream.Stream;
import org.jwcarman.odyssey.core.OdysseyEvent;

public interface OdysseyEventLog {

  String ephemeralKey();

  String channelKey(String name);

  String broadcastKey(String name);

  String append(String streamKey, OdysseyEvent event);

  Stream<OdysseyEvent> readAfter(String streamKey, String lastId);

  Stream<OdysseyEvent> readLast(String streamKey, int count);

  void delete(String streamKey);
}
