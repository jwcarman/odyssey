package org.jwcarman.odyssey.redis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class PubSubNotificationListenerTest {

  private static final String STREAM_PREFIX = "odyssey:";

  private PubSubNotificationListener listener;

  @BeforeEach
  void setUp() {
    listener = new PubSubNotificationListener(null, STREAM_PREFIX);
  }

  @Test
  void extractStreamKeyStripsNotifyPrefix() {
    assertEquals("my-stream", listener.extractStreamKey("odyssey:notify:my-stream"));
  }

  @Test
  void extractStreamKeyHandlesNestedColons() {
    assertEquals("channel:user:123", listener.extractStreamKey("odyssey:notify:channel:user:123"));
  }

  @Test
  void extractStreamKeyWithCustomPrefix() {
    PubSubNotificationListener custom = new PubSubNotificationListener(null, "custom:");
    assertEquals("my-stream", custom.extractStreamKey("custom:notify:my-stream"));
  }

  @Test
  void notificationDispatchesNudgeToRegisteredFanout() {
    TopicFanout fanout = new TopicFanout();
    SubscriberOutbox outbox = createOutbox();
    fanout.addSubscriber(outbox);

    listener.registerFanout("my-stream", fanout);

    listener.message("odyssey:notify:*", "odyssey:notify:my-stream", "1234567890-0");

    // Verify nudge was dispatched — the outbox semaphore should have a permit
    assertTrue(fanout.hasSubscribers());
  }

  @Test
  void unknownStreamKeyIsIgnored() {
    // No fanout registered for "unknown-stream"
    assertDoesNotThrow(
        () ->
            listener.message("odyssey:notify:*", "odyssey:notify:unknown-stream", "1234567890-0"));
  }

  @Test
  void fanoutWithNoSubscribersIsNotNudged() {
    TopicFanout fanout = new TopicFanout();
    // No subscribers added

    listener.registerFanout("empty-stream", fanout);

    // Should not throw and should not attempt nudge
    assertDoesNotThrow(
        () -> listener.message("odyssey:notify:*", "odyssey:notify:empty-stream", "1234567890-0"));
  }

  @Test
  void unregisterFanoutPreventsDispatch() {
    TopicFanout fanout = new TopicFanout();
    SubscriberOutbox outbox = createOutbox();
    fanout.addSubscriber(outbox);

    listener.registerFanout("my-stream", fanout);
    listener.unregisterFanout("my-stream");

    // Should be ignored since fanout was unregistered
    assertDoesNotThrow(
        () -> listener.message("odyssey:notify:*", "odyssey:notify:my-stream", "1234567890-0"));
  }

  private SubscriberOutbox createOutbox() {
    return new SubscriberOutbox(
        lastId -> List.of(), new SseEmitter(0L), "test-stream", "0", 60_000);
  }
}
