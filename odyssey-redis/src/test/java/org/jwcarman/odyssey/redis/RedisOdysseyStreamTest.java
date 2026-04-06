package org.jwcarman.odyssey.redis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisOdysseyStreamTest {

  private static final String STREAM_KEY = "odyssey:channel:test";
  private static final String STREAM_PREFIX = "odyssey:";
  private static final String NOTIFY_CHANNEL = "odyssey:notify:" + STREAM_KEY;
  private static final long KEEP_ALIVE = 30_000;
  private static final long SSE_TIMEOUT = 0;
  private static final long MAX_LEN = 100_000;
  private static final int MAX_LAST_N = 500;

  @Mock private RedisCommands<String, String> commands;

  @Captor private ArgumentCaptor<Map<String, String>> bodyCaptor;

  private TopicFanout fanout;
  private RedisOdysseyStream stream;

  @BeforeEach
  void setUp() {
    fanout = spy(new TopicFanout());
    stream =
        new RedisOdysseyStream(
            STREAM_KEY,
            commands,
            fanout,
            KEEP_ALIVE,
            SSE_TIMEOUT,
            MAX_LEN,
            MAX_LAST_N,
            STREAM_PREFIX,
            0);
  }

  @AfterEach
  void tearDown() {
    stream.close();
  }

  @Test
  void publishPerformsXaddAndPublishAndReturnsEntryId() {
    when(commands.xadd(eq(STREAM_KEY), any(XAddArgs.class), anyMap())).thenReturn("1-0");

    String entryId = stream.publish("test-event", "{\"data\":1}");

    assertEquals("1-0", entryId);
    verify(commands).xadd(eq(STREAM_KEY), any(XAddArgs.class), bodyCaptor.capture());
    Map<String, String> body = bodyCaptor.getValue();
    assertEquals("test-event", body.get("eventType"));
    assertEquals("{\"data\":1}", body.get("payload"));
    assertNotNull(body.get("timestamp"));
    verify(commands).publish(NOTIFY_CHANNEL, "1-0");
  }

  @Test
  void subscribeReturnsEmitter() {
    lenient().when(commands.xrevrange(eq(STREAM_KEY), any(), any())).thenReturn(List.of());

    var emitter = stream.subscribe();

    assertNotNull(emitter);
  }

  @Test
  void subscribeWithTimeoutReturnsEmitter() {
    lenient().when(commands.xrevrange(eq(STREAM_KEY), any(), any())).thenReturn(List.of());

    var emitter = stream.subscribe(Duration.ofSeconds(60));

    assertNotNull(emitter);
  }

  @Test
  void subscribeQueriesCurrentLastId() {
    when(commands.xrevrange(eq(STREAM_KEY), any(), any())).thenReturn(List.of());

    stream.subscribe();

    verify(commands).xrevrange(eq(STREAM_KEY), any(Range.class), any(Limit.class));
  }

  @Test
  void resumeAfterCallsXrangeWithExclusiveRange() {
    when(commands.xrange(eq(STREAM_KEY), any())).thenReturn(List.of());

    stream.resumeAfter("5-0");

    verify(commands).xrange(eq(STREAM_KEY), any(Range.class));
  }

  @Test
  void resumeAfterReturnsEmitter() {
    when(commands.xrange(eq(STREAM_KEY), any())).thenReturn(List.of());

    var emitter = stream.resumeAfter("5-0");

    assertNotNull(emitter);
  }

  @Test
  void resumeAfterWithTimeoutReturnsEmitter() {
    when(commands.xrange(eq(STREAM_KEY), any())).thenReturn(List.of());

    var emitter = stream.resumeAfter("5-0", Duration.ofSeconds(60));

    assertNotNull(emitter);
  }

  @Test
  void resumeAfterReplaysEventsFromXrange() {
    var msg1 = new StreamMessage<>(STREAM_KEY, "6-0", messageBody("evt1", "p1"));
    var msg2 = new StreamMessage<>(STREAM_KEY, "7-0", messageBody("evt2", "p2"));
    when(commands.xrange(eq(STREAM_KEY), any())).thenReturn(List.of(msg1, msg2));

    var emitter = stream.resumeAfter("5-0");

    assertNotNull(emitter);
    verify(commands).xrange(eq(STREAM_KEY), any(Range.class));
  }

  @Test
  void replayLastCallsXrevrange() {
    when(commands.xrevrange(eq(STREAM_KEY), any(), any())).thenReturn(List.of());

    stream.replayLast(10);

    // Called twice: once for the replayLast query, once for getCurrentLastId (empty replay)
    verify(commands, times(2)).xrevrange(eq(STREAM_KEY), any(Range.class), any(Limit.class));
  }

  @Test
  void replayLastCapsCountAtMaxLastN() {
    when(commands.xrevrange(eq(STREAM_KEY), any(), any())).thenReturn(List.of());

    stream.replayLast(1000);

    // Called twice: once for the replayLast query (capped at 500), once for getCurrentLastId
    verify(commands, times(2)).xrevrange(eq(STREAM_KEY), any(Range.class), any(Limit.class));
  }

  @Test
  void replayLastReturnsEmitter() {
    when(commands.xrevrange(eq(STREAM_KEY), any(), any())).thenReturn(List.of());

    var emitter = stream.replayLast(5);

    assertNotNull(emitter);
  }

  @Test
  void replayLastWithTimeoutReturnsEmitter() {
    when(commands.xrevrange(eq(STREAM_KEY), any(), any())).thenReturn(List.of());

    var emitter = stream.replayLast(5, Duration.ofSeconds(60));

    assertNotNull(emitter);
  }

  @Test
  void closeShutsFanoutGracefully() {
    stream.close();

    verify(fanout).shutdown();
  }

  @Test
  void deleteShutsFanoutImmediatelyAndDelsKey() {
    stream.delete();

    verify(fanout).shutdownImmediately();
    verify(commands).del(STREAM_KEY);
  }

  @Test
  void publishRefreshesTtlWhenTtlIsPositive() {
    RedisOdysseyStream streamWithTtl =
        new RedisOdysseyStream(
            STREAM_KEY,
            commands,
            fanout,
            KEEP_ALIVE,
            SSE_TIMEOUT,
            MAX_LEN,
            MAX_LAST_N,
            STREAM_PREFIX,
            300);
    when(commands.xadd(eq(STREAM_KEY), any(XAddArgs.class), anyMap())).thenReturn("1-0");

    streamWithTtl.publish("test-event", "payload");

    verify(commands).expire(STREAM_KEY, 300);
  }

  @Test
  void publishSkipsExpireWhenTtlIsZero() {
    when(commands.xadd(eq(STREAM_KEY), any(XAddArgs.class), anyMap())).thenReturn("1-0");

    stream.publish("test-event", "payload");

    verify(commands, never()).expire(anyString(), anyLong());
  }

  @Test
  void getStreamKeyReturnsKey() {
    assertEquals(STREAM_KEY, stream.getStreamKey());
  }

  private static Map<String, String> messageBody(String eventType, String payload) {
    return Map.of(
        "eventType", eventType,
        "payload", payload,
        "timestamp", Instant.now().toString());
  }
}
