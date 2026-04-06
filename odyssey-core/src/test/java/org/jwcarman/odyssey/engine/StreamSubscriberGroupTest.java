package org.jwcarman.odyssey.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.StreamEventHandler;
import org.jwcarman.odyssey.spi.OdysseyEventLog;

class StreamSubscriberGroupTest {

  private StreamSubscriber createSubscriber() {
    OdysseyEventLog eventLog = mock(OdysseyEventLog.class);
    when(eventLog.readAfter(anyString(), anyString())).thenReturn(Stream.empty());
    StreamEventHandler handler = mock(StreamEventHandler.class);
    return new StreamSubscriber(eventLog, handler, "test-stream", "0", 60_000);
  }

  @Test
  void addSubscriberRegistersSubscriber() {
    StreamSubscriberGroup group = new StreamSubscriberGroup();
    StreamSubscriber subscriber = createSubscriber();

    group.addSubscriber(subscriber);

    assertTrue(group.hasSubscribers());
  }

  @Test
  void removeSubscriberUnregistersSubscriber() {
    StreamSubscriberGroup group = new StreamSubscriberGroup();
    StreamSubscriber subscriber = createSubscriber();

    group.addSubscriber(subscriber);
    group.removeSubscriber(subscriber);

    assertFalse(group.hasSubscribers());
  }

  @Test
  void hasSubscribersReturnsFalseWhenEmpty() {
    StreamSubscriberGroup group = new StreamSubscriberGroup();

    assertFalse(group.hasSubscribers());
  }

  @Test
  void nudgeAllReachesAllSubscribers() {
    StreamSubscriberGroup group = new StreamSubscriberGroup();
    StreamSubscriber subscriber1 = createSubscriber();
    StreamSubscriber subscriber2 = createSubscriber();

    group.addSubscriber(subscriber1);
    group.addSubscriber(subscriber2);

    assertDoesNotThrow(group::nudgeAll);
    assertTrue(group.hasSubscribers());
  }

  @Test
  void shutdownClosesAllSubscribersGracefully() {
    StreamSubscriberGroup group = new StreamSubscriberGroup();
    StreamSubscriber subscriber1 = createSubscriber();
    StreamSubscriber subscriber2 = createSubscriber();

    group.addSubscriber(subscriber1);
    group.addSubscriber(subscriber2);

    group.shutdown();

    assertTrue(group.hasSubscribers(), "Shutdown does not remove subscribers from the list");
  }

  @Test
  void addAndRemoveMultipleSubscribers() {
    StreamSubscriberGroup group = new StreamSubscriberGroup();
    StreamSubscriber subscriber1 = createSubscriber();
    StreamSubscriber subscriber2 = createSubscriber();
    StreamSubscriber subscriber3 = createSubscriber();

    group.addSubscriber(subscriber1);
    group.addSubscriber(subscriber2);
    group.addSubscriber(subscriber3);

    assertTrue(group.hasSubscribers());

    group.removeSubscriber(subscriber2);
    assertTrue(group.hasSubscribers());

    group.removeSubscriber(subscriber1);
    assertTrue(group.hasSubscribers());

    group.removeSubscriber(subscriber3);
    assertFalse(group.hasSubscribers());
  }

  @Test
  void nudgeAllOnEmptyGroupDoesNotThrow() {
    StreamSubscriberGroup group = new StreamSubscriberGroup();

    assertDoesNotThrow(group::nudgeAll);
  }

  @Test
  void shutdownOnEmptyGroupDoesNotThrow() {
    StreamSubscriberGroup group = new StreamSubscriberGroup();

    assertDoesNotThrow(group::shutdown);
  }
}
