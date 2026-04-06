package org.jwcarman.odyssey.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitterTestSupport;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitterTestSupport.CapturedSseEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitterTestSupport.SseCapture;

@Timeout(30)
class RedisStreamIntegrationTest extends AbstractRedisIntegrationTest {

  @Test
  void publishAndSubscribeLive() throws Exception {
    OdysseyStream stream = registry.channel("live-test");
    SseEmitter emitter = stream.subscribe();
    SseCapture capture = SseEmitterTestSupport.capture(emitter);

    String eventId = stream.publish("greeting", "hello");

    CapturedSseEvent event = capture.poll(5, TimeUnit.SECONDS);
    assertThat(event).isNotNull();
    assertThat(event.id()).isEqualTo(eventId);
    assertThat(event.eventType()).isEqualTo("greeting");
    assertThat(event.data()).isEqualTo("hello");

    stream.close();
    assertThat(capture.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void multipleSubscribers() throws Exception {
    OdysseyStream stream = registry.channel("multi-test");

    SseEmitter emitter1 = stream.subscribe();
    SseCapture capture1 = SseEmitterTestSupport.capture(emitter1);

    SseEmitter emitter2 = stream.subscribe();
    SseCapture capture2 = SseEmitterTestSupport.capture(emitter2);

    String eventId = stream.publish("msg", "shared");

    CapturedSseEvent event1 = capture1.poll(5, TimeUnit.SECONDS);
    CapturedSseEvent event2 = capture2.poll(5, TimeUnit.SECONDS);

    assertThat(event1).isNotNull();
    assertThat(event1.id()).isEqualTo(eventId);
    assertThat(event1.data()).isEqualTo("shared");

    assertThat(event2).isNotNull();
    assertThat(event2.id()).isEqualTo(eventId);
    assertThat(event2.data()).isEqualTo("shared");

    stream.close();
  }

  @Test
  void resumeAfterDisconnect() throws Exception {
    OdysseyStream stream = registry.channel("resume-test");

    String id1 = stream.publish("evt", "one");
    String id2 = stream.publish("evt", "two");
    String id3 = stream.publish("evt", "three");

    // Resume after the first event — should receive events 2 and 3
    SseEmitter emitter = stream.resumeAfter(id1);
    SseCapture capture = SseEmitterTestSupport.capture(emitter);

    List<CapturedSseEvent> replayed = capture.pollEvents(2, 5, TimeUnit.SECONDS);
    assertThat(replayed).hasSize(2);
    assertThat(replayed.get(0).id()).isEqualTo(id2);
    assertThat(replayed.get(0).data()).isEqualTo("two");
    assertThat(replayed.get(1).id()).isEqualTo(id3);
    assertThat(replayed.get(1).data()).isEqualTo("three");

    // Verify live events continue after replay
    String id4 = stream.publish("evt", "four");
    CapturedSseEvent live = capture.poll(5, TimeUnit.SECONDS);
    assertThat(live).isNotNull();
    assertThat(live.id()).isEqualTo(id4);
    assertThat(live.data()).isEqualTo("four");

    stream.close();
  }

  @Test
  void replayLastN() throws Exception {
    OdysseyStream stream = registry.channel("replay-test");

    stream.publish("evt", "a");
    stream.publish("evt", "b");
    String id3 = stream.publish("evt", "c");
    String id4 = stream.publish("evt", "d");
    String id5 = stream.publish("evt", "e");

    // Replay last 3 events
    SseEmitter emitter = stream.replayLast(3);
    SseCapture capture = SseEmitterTestSupport.capture(emitter);

    List<CapturedSseEvent> replayed = capture.pollEvents(3, 5, TimeUnit.SECONDS);
    assertThat(replayed).hasSize(3);
    assertThat(replayed.get(0).id()).isEqualTo(id3);
    assertThat(replayed.get(0).data()).isEqualTo("c");
    assertThat(replayed.get(1).id()).isEqualTo(id4);
    assertThat(replayed.get(1).data()).isEqualTo("d");
    assertThat(replayed.get(2).id()).isEqualTo(id5);
    assertThat(replayed.get(2).data()).isEqualTo("e");

    // Verify live events continue
    String id6 = stream.publish("evt", "f");
    CapturedSseEvent live = capture.poll(5, TimeUnit.SECONDS);
    assertThat(live).isNotNull();
    assertThat(live.id()).isEqualTo(id6);
    assertThat(live.data()).isEqualTo("f");

    stream.close();
  }

  @Test
  void ephemeralClose() throws Exception {
    OdysseyStream stream = registry.ephemeral();

    SseEmitter emitter = stream.subscribe();
    SseCapture capture = SseEmitterTestSupport.capture(emitter);

    stream.publish("progress", "step1");
    stream.publish("progress", "step2");
    stream.publish("result", "done");

    // Wait for all 3 events to be received before closing
    List<CapturedSseEvent> events = capture.pollEvents(3, 5, TimeUnit.SECONDS);
    assertThat(events).extracting(CapturedSseEvent::data).containsExactly("step1", "step2", "done");

    // Close the stream — subscriber should complete gracefully
    stream.close();
    assertThat(capture.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void deleteDisconnectsSubscriber() throws Exception {
    OdysseyStream stream = registry.channel("delete-test");

    SseEmitter emitter = stream.subscribe();
    SseCapture capture = SseEmitterTestSupport.capture(emitter);

    stream.publish("msg", "before-delete");

    // Verify the event arrives before delete
    CapturedSseEvent event = capture.poll(5, TimeUnit.SECONDS);
    assertThat(event).isNotNull();
    assertThat(event.data()).isEqualTo("before-delete");

    stream.delete();

    // Verify the Redis key was deleted
    assertThat(commands.exists(stream.getStreamKey())).isEqualTo(0L);

    // After delete, no new events should be deliverable. Publishing to a deleted stream
    // still adds to Redis (the key is recreated), but the subscriber threads were interrupted
    // so no further events reach the capture.
    stream.publish("msg", "after-delete");
    CapturedSseEvent ghost = capture.poll(2, TimeUnit.SECONDS);
    assertThat(ghost).isNull();
  }

  @Test
  void keepAlive() throws Exception {
    // Use a short keep-alive interval for this test
    RedisOdysseyStreamRegistry shortKeepAliveRegistry = createRegistry(1_000);

    OdysseyStream stream = shortKeepAliveRegistry.channel("keepalive-test");
    SseEmitter emitter = stream.subscribe();
    SseCapture capture = SseEmitterTestSupport.capture(emitter);

    // Wait for a keep-alive comment (should arrive within ~1 second)
    CapturedSseEvent event = capture.poll(3, TimeUnit.SECONDS);
    assertThat(event).isNotNull();
    assertThat(event.isKeepAlive()).isTrue();

    stream.close();
  }

  @Test
  void crossTypeConsistency() throws Exception {
    // Test that ephemeral, channel, and broadcast all handle basic publish/subscribe identically
    OdysseyStream ephemeral = registry.ephemeral();
    OdysseyStream channel = registry.channel("cross-type-test");
    OdysseyStream broadcast = registry.broadcast("cross-type-test");

    SseEmitter emitterE = ephemeral.subscribe();
    SseCapture captureE = SseEmitterTestSupport.capture(emitterE);

    SseEmitter emitterC = channel.subscribe();
    SseCapture captureC = SseEmitterTestSupport.capture(emitterC);

    SseEmitter emitterB = broadcast.subscribe();
    SseCapture captureB = SseEmitterTestSupport.capture(emitterB);

    String idE = ephemeral.publish("test", "ephemeral-data");
    String idC = channel.publish("test", "channel-data");
    String idB = broadcast.publish("test", "broadcast-data");

    CapturedSseEvent eventE = captureE.poll(5, TimeUnit.SECONDS);
    CapturedSseEvent eventC = captureC.poll(5, TimeUnit.SECONDS);
    CapturedSseEvent eventB = captureB.poll(5, TimeUnit.SECONDS);

    assertThat(eventE).isNotNull();
    assertThat(eventE.id()).isEqualTo(idE);
    assertThat(eventE.eventType()).isEqualTo("test");
    assertThat(eventE.data()).isEqualTo("ephemeral-data");

    assertThat(eventC).isNotNull();
    assertThat(eventC.id()).isEqualTo(idC);
    assertThat(eventC.eventType()).isEqualTo("test");
    assertThat(eventC.data()).isEqualTo("channel-data");

    assertThat(eventB).isNotNull();
    assertThat(eventB.id()).isEqualTo(idB);
    assertThat(eventB.eventType()).isEqualTo("test");
    assertThat(eventB.data()).isEqualTo("broadcast-data");

    ephemeral.close();
    channel.close();
    broadcast.close();
  }
}
