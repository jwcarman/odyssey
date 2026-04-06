package org.jwcarman.odyssey.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryOdysseyStreamNotifierTest {

  private InMemoryOdysseyStreamNotifier notifier;

  @BeforeEach
  void setUp() {
    notifier = new InMemoryOdysseyStreamNotifier();
  }

  @Test
  void notifyCallsRegisteredHandler() {
    List<String> received = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> received.add(streamKey + ":" + eventId));

    notifier.notify("odyssey:channel:test", "1-0");

    assertEquals(1, received.size());
    assertEquals("odyssey:channel:test:1-0", received.getFirst());
  }

  @Test
  void notifyCallsMultipleHandlers() {
    List<String> handler1 = new ArrayList<>();
    List<String> handler2 = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> handler1.add(eventId));
    notifier.subscribe((streamKey, eventId) -> handler2.add(eventId));

    notifier.notify("odyssey:channel:test", "1-0");

    assertEquals(1, handler1.size());
    assertEquals(1, handler2.size());
  }

  @Test
  void notifyWithNoHandlersDoesNotThrow() {
    assertDoesNotThrow(() -> notifier.notify("odyssey:channel:test", "1-0"));
  }

  @Test
  void handlersReceiveCorrectStreamKeyAndEventId() {
    List<String> keys = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    notifier.subscribe(
        (streamKey, eventId) -> {
          keys.add(streamKey);
          ids.add(eventId);
        });

    notifier.notify("odyssey:channel:alpha", "5-0");
    notifier.notify("odyssey:broadcast:beta", "6-0");

    assertEquals(List.of("odyssey:channel:alpha", "odyssey:broadcast:beta"), keys);
    assertEquals(List.of("5-0", "6-0"), ids);
  }

  @Test
  void multipleNotificationsDeliveredInOrder() {
    List<String> received = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> received.add(eventId));

    notifier.notify("odyssey:channel:test", "1-0");
    notifier.notify("odyssey:channel:test", "2-0");
    notifier.notify("odyssey:channel:test", "3-0");

    assertEquals(List.of("1-0", "2-0", "3-0"), received);
  }

  @Test
  void notifyIsSynchronous() {
    List<String> order = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> order.add("handler-" + eventId));

    notifier.notify("odyssey:channel:test", "1-0");
    order.add("after-notify");

    assertEquals(List.of("handler-1-0", "after-notify"), order);
  }
}
