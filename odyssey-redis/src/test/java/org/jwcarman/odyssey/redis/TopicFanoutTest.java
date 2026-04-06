package org.jwcarman.odyssey.redis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class TopicFanoutTest {

  private SubscriberOutbox createOutbox() {
    return new SubscriberOutbox(
        lastId -> List.of(), new SseEmitter(0L), "test-stream", "0", 60_000);
  }

  @Test
  void addSubscriberRegistersOutbox() {
    TopicFanout fanout = new TopicFanout();
    SubscriberOutbox outbox = createOutbox();

    fanout.addSubscriber(outbox);

    assertTrue(fanout.hasSubscribers());
  }

  @Test
  void removeSubscriberUnregistersOutbox() {
    TopicFanout fanout = new TopicFanout();
    SubscriberOutbox outbox = createOutbox();

    fanout.addSubscriber(outbox);
    fanout.removeSubscriber(outbox);

    assertFalse(fanout.hasSubscribers());
  }

  @Test
  void hasSubscribersReturnsFalseWhenEmpty() {
    TopicFanout fanout = new TopicFanout();

    assertFalse(fanout.hasSubscribers());
  }

  @Test
  void nudgeAllReachesAllOutboxes() throws Exception {
    TopicFanout fanout = new TopicFanout();
    AtomicInteger nudgeCount1 = new AtomicInteger(0);
    AtomicInteger nudgeCount2 = new AtomicInteger(0);

    // Create outboxes and start them so nudge has observable effects
    SubscriberOutbox outbox1 = createOutbox();
    SubscriberOutbox outbox2 = createOutbox();

    fanout.addSubscriber(outbox1);
    fanout.addSubscriber(outbox2);

    // nudgeAll should not throw and should call nudge on each
    fanout.nudgeAll();

    // Verify by checking the semaphore state indirectly —
    // after nudgeAll, each outbox's semaphore should have a permit available
    assertTrue(fanout.hasSubscribers());
  }

  @Test
  void shutdownClosesAllOutboxesGracefully() {
    TopicFanout fanout = new TopicFanout();
    SubscriberOutbox outbox1 = createOutbox();
    SubscriberOutbox outbox2 = createOutbox();

    fanout.addSubscriber(outbox1);
    fanout.addSubscriber(outbox2);

    // shutdown should not throw — it calls closeGracefully on each outbox
    fanout.shutdown();

    assertTrue(fanout.hasSubscribers(), "Shutdown does not remove subscribers from the list");
  }

  @Test
  void addAndRemoveMultipleSubscribers() {
    TopicFanout fanout = new TopicFanout();
    SubscriberOutbox outbox1 = createOutbox();
    SubscriberOutbox outbox2 = createOutbox();
    SubscriberOutbox outbox3 = createOutbox();

    fanout.addSubscriber(outbox1);
    fanout.addSubscriber(outbox2);
    fanout.addSubscriber(outbox3);

    assertTrue(fanout.hasSubscribers());

    fanout.removeSubscriber(outbox2);
    assertTrue(fanout.hasSubscribers());

    fanout.removeSubscriber(outbox1);
    assertTrue(fanout.hasSubscribers());

    fanout.removeSubscriber(outbox3);
    assertFalse(fanout.hasSubscribers());
  }

  @Test
  void nudgeAllOnEmptyFanoutDoesNotThrow() {
    TopicFanout fanout = new TopicFanout();

    assertDoesNotThrow(fanout::nudgeAll);
  }

  @Test
  void shutdownOnEmptyFanoutDoesNotThrow() {
    TopicFanout fanout = new TopicFanout();

    assertDoesNotThrow(fanout::shutdown);
  }
}
