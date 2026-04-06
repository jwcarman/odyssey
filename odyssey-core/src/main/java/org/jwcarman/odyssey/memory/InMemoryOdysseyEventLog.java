package org.jwcarman.odyssey.memory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.spi.OdysseyEventLog;

public class InMemoryOdysseyEventLog implements OdysseyEventLog {

  private final ConcurrentMap<String, BoundedEventList> streams = new ConcurrentHashMap<>();
  private final int maxLen;
  private final AtomicLong counter = new AtomicLong(0);

  private static final int DEFAULT_MAX_LEN = 100_000;

  public InMemoryOdysseyEventLog() {
    this(DEFAULT_MAX_LEN);
  }

  public InMemoryOdysseyEventLog(int maxLen) {
    this.maxLen = maxLen;
  }

  @Override
  public String ephemeralKey() {
    return "ephemeral:" + UUID.randomUUID();
  }

  @Override
  public String channelKey(String name) {
    return "channel:" + name;
  }

  @Override
  public String broadcastKey(String name) {
    return "broadcast:" + name;
  }

  @Override
  public String append(String streamKey, OdysseyEvent event) {
    String eventId = counter.incrementAndGet() + "-0";
    OdysseyEvent stored =
        OdysseyEvent.builder()
            .id(eventId)
            .streamKey(event.streamKey())
            .eventType(event.eventType())
            .payload(event.payload())
            .timestamp(event.timestamp())
            .metadata(event.metadata())
            .build();
    streams.computeIfAbsent(streamKey, k -> new BoundedEventList(maxLen)).add(stored);
    return eventId;
  }

  @Override
  public Stream<OdysseyEvent> readAfter(String streamKey, String lastId) {
    BoundedEventList list = streams.get(streamKey);
    if (list == null) {
      return Stream.empty();
    }
    long cursor = parseId(lastId);
    return list.snapshot().stream().filter(e -> parseId(e.id()) > cursor);
  }

  @Override
  public Stream<OdysseyEvent> readLast(String streamKey, int count) {
    BoundedEventList list = streams.get(streamKey);
    if (list == null) {
      return Stream.empty();
    }
    List<OdysseyEvent> snapshot = list.snapshot();
    int start = Math.max(0, snapshot.size() - count);
    return snapshot.subList(start, snapshot.size()).stream();
  }

  @Override
  public void delete(String streamKey) {
    streams.remove(streamKey);
  }

  private static long parseId(String id) {
    int dash = id.indexOf('-');
    return Long.parseLong(dash >= 0 ? id.substring(0, dash) : id);
  }

  private static final class BoundedEventList {

    private final int maxSize;
    private final LinkedList<OdysseyEvent> events = new LinkedList<>();

    BoundedEventList(int maxSize) {
      this.maxSize = maxSize;
    }

    synchronized void add(OdysseyEvent event) {
      events.addLast(event);
      while (events.size() > maxSize) {
        events.removeFirst();
      }
    }

    synchronized List<OdysseyEvent> snapshot() {
      return new ArrayList<>(events);
    }
  }
}
