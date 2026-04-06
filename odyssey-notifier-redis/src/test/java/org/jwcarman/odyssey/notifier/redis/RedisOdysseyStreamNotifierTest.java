package org.jwcarman.odyssey.notifier.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisOdysseyStreamNotifierTest {

  @Mock private StatefulRedisPubSubConnection<String, String> pubSubConnection;
  @Mock private RedisPubSubCommands<String, String> pubSubCommands;
  @Mock private RedisCommands<String, String> sharedCommands;

  private RedisOdysseyStreamNotifier notifier;

  @BeforeEach
  void setUp() {
    when(pubSubConnection.sync()).thenReturn(pubSubCommands);
    notifier = new RedisOdysseyStreamNotifier(pubSubConnection, sharedCommands, "odyssey:notify:");
  }

  @Test
  void notifyPublishesEventIdToNotifyChannel() {
    notifier.notify("odyssey:channel:my-channel", "1712404800000-0");

    verify(sharedCommands).publish("odyssey:notify:odyssey:channel:my-channel", "1712404800000-0");
  }

  @Test
  void subscribeDispatchesIncomingMessagesToHandler() {
    List<String> received = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> received.add(streamKey + ":" + eventId));

    notifier.message("odyssey:notify:*", "odyssey:notify:odyssey:channel:test", "42-0");

    assertThat(received).containsExactly("odyssey:channel:test:42-0");
  }

  @Test
  void streamKeyIsExtractedByStrippingNotifyPrefix() {
    List<String> streamKeys = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> streamKeys.add(streamKey));

    notifier.message("odyssey:notify:*", "odyssey:notify:odyssey:ephemeral:abc-123", "1-0");

    assertThat(streamKeys).containsExactly("odyssey:ephemeral:abc-123");
  }

  @Test
  void startIssuesPsubscribeOnDedicatedConnection() {
    notifier.start();

    verify(pubSubConnection).addListener(notifier);
    verify(pubSubCommands).psubscribe("odyssey:notify:*");
    assertThat(notifier.isRunning()).isTrue();
  }

  @Test
  void stopUnsubscribesAndRemovesListener() {
    notifier.start();
    notifier.stop();

    verify(pubSubCommands).punsubscribe("odyssey:notify:*");
    verify(pubSubConnection).removeListener(notifier);
    assertThat(notifier.isRunning()).isFalse();
  }

  @Test
  void multipleHandlersAllReceiveNotifications() {
    List<String> handler1Received = new ArrayList<>();
    List<String> handler2Received = new ArrayList<>();

    notifier.subscribe((streamKey, eventId) -> handler1Received.add(eventId));
    notifier.subscribe((streamKey, eventId) -> handler2Received.add(eventId));

    notifier.message("odyssey:notify:*", "odyssey:notify:odyssey:broadcast:news", "5-0");

    assertThat(handler1Received).containsExactly("5-0");
    assertThat(handler2Received).containsExactly("5-0");
  }
}
