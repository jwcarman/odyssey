/*
 * Copyright © 2026 James Carman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwcarman.odyssey.eventlog.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisOdysseyEventLogTest {

  @Mock private RedisCommands<String, String> commands;

  private RedisOdysseyEventLog eventLog;

  @BeforeEach
  void setUp() {
    eventLog =
        new RedisOdysseyEventLog(
            commands,
            100_000,
            "odyssey:ephemeral:",
            "odyssey:channel:",
            "odyssey:broadcast:",
            300,
            3600,
            86400);
  }

  @Test
  void appendPerformsXaddWithMaxlenTrimming() {
    Instant now = Instant.parse("2026-04-06T12:00:00Z");
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType("test")
            .payload("{\"key\":\"value\"}")
            .timestamp(now)
            .streamKey("odyssey:channel:my-channel")
            .build();

    when(commands.xadd(eq("odyssey:channel:my-channel"), any(XAddArgs.class), any(Map.class)))
        .thenReturn("1712404800000-0");
    when(commands.expire("odyssey:channel:my-channel", 3600)).thenReturn(true);

    String entryId = eventLog.append("odyssey:channel:my-channel", event);

    assertThat(entryId).isEqualTo("1712404800000-0");

    verify(commands)
        .xadd(
            eq("odyssey:channel:my-channel"),
            any(XAddArgs.class),
            argThat(
                (Map<String, String> body) ->
                    "test".equals(body.get("eventType"))
                        && "{\"key\":\"value\"}".equals(body.get("payload"))
                        && now.toString().equals(body.get("timestamp"))));
  }

  @Test
  void appendRefreshesTtlViaExpire() {
    Instant now = Instant.now();
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType("test")
            .payload("data")
            .timestamp(now)
            .streamKey("odyssey:ephemeral:abc")
            .build();

    when(commands.xadd(eq("odyssey:ephemeral:abc"), any(XAddArgs.class), any(Map.class)))
        .thenReturn("1-0");
    when(commands.expire("odyssey:ephemeral:abc", 300)).thenReturn(true);

    eventLog.append("odyssey:ephemeral:abc", event);

    verify(commands).expire("odyssey:ephemeral:abc", 300);
  }

  @Test
  void appendIncludesMetadataFields() {
    Instant now = Instant.now();
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType("test")
            .payload("data")
            .timestamp(now)
            .streamKey("odyssey:channel:test")
            .metadata(Map.of("userId", "user-123", "source", "api"))
            .build();

    when(commands.xadd(eq("odyssey:channel:test"), any(XAddArgs.class), any(Map.class)))
        .thenReturn("1-0");
    when(commands.expire("odyssey:channel:test", 3600)).thenReturn(true);

    eventLog.append("odyssey:channel:test", event);

    verify(commands)
        .xadd(
            eq("odyssey:channel:test"),
            any(XAddArgs.class),
            argThat(
                (Map<String, String> body) ->
                    "user-123".equals(body.get("userId")) && "api".equals(body.get("source"))));
  }

  @Test
  void readAfterReturnsEventsAfterGivenId() {
    StreamMessage<String, String> msg1 =
        new StreamMessage<>(
            "odyssey:channel:test",
            "2-0",
            Map.of("eventType", "a", "payload", "p1", "timestamp", "2026-04-06T12:00:00Z"));
    StreamMessage<String, String> msg2 =
        new StreamMessage<>(
            "odyssey:channel:test",
            "3-0",
            Map.of("eventType", "b", "payload", "p2", "timestamp", "2026-04-06T12:01:00Z"));

    when(commands.xread(any(XReadArgs.class), any(XReadArgs.StreamOffset[].class)))
        .thenReturn(List.of(msg1, msg2));

    Stream<OdysseyEvent> result = eventLog.readAfter("odyssey:channel:test", "1-0");
    List<OdysseyEvent> events = result.toList();

    assertThat(events).hasSize(2);
    assertThat(events.get(0).id()).isEqualTo("2-0");
    assertThat(events.get(0).eventType()).isEqualTo("a");
    assertThat(events.get(0).payload()).isEqualTo("p1");
    assertThat(events.get(0).streamKey()).isEqualTo("odyssey:channel:test");
    assertThat(events.get(1).id()).isEqualTo("3-0");
    assertThat(events.get(1).eventType()).isEqualTo("b");
  }

  @Test
  void readAfterReturnsEmptyStreamWhenNoMessages() {
    when(commands.xread(any(XReadArgs.class), any(XReadArgs.StreamOffset[].class)))
        .thenReturn(null);

    Stream<OdysseyEvent> result = eventLog.readAfter("odyssey:channel:test", "0-0");

    assertThat(result.toList()).isEmpty();
  }

  @Test
  void readLastReturnsEventsInChronologicalOrder() {
    StreamMessage<String, String> msg2 =
        new StreamMessage<>(
            "odyssey:channel:test",
            "2-0",
            Map.of("eventType", "b", "payload", "p2", "timestamp", "2026-04-06T12:01:00Z"));
    StreamMessage<String, String> msg1 =
        new StreamMessage<>(
            "odyssey:channel:test",
            "1-0",
            Map.of("eventType", "a", "payload", "p1", "timestamp", "2026-04-06T12:00:00Z"));

    when(commands.xrevrange(eq("odyssey:channel:test"), any(Range.class), any(Limit.class)))
        .thenReturn(List.of(msg2, msg1));

    Stream<OdysseyEvent> result = eventLog.readLast("odyssey:channel:test", 2);
    List<OdysseyEvent> events = result.toList();

    assertThat(events).hasSize(2);
    assertThat(events.get(0).id()).isEqualTo("1-0");
    assertThat(events.get(0).eventType()).isEqualTo("a");
    assertThat(events.get(1).id()).isEqualTo("2-0");
    assertThat(events.get(1).eventType()).isEqualTo("b");
  }

  @Test
  void deleteRemovesTheRedisKey() {
    when(commands.del("odyssey:channel:test")).thenReturn(1L);

    eventLog.delete("odyssey:channel:test");

    verify(commands).del("odyssey:channel:test");
  }

  @Test
  void appendUsesEphemeralTtlForEphemeralStreams() {
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType("test")
            .payload("data")
            .timestamp(Instant.now())
            .streamKey("odyssey:ephemeral:uuid-123")
            .build();

    when(commands.xadd(eq("odyssey:ephemeral:uuid-123"), any(XAddArgs.class), any(Map.class)))
        .thenReturn("1-0");
    when(commands.expire("odyssey:ephemeral:uuid-123", 300)).thenReturn(true);

    eventLog.append("odyssey:ephemeral:uuid-123", event);

    verify(commands).expire("odyssey:ephemeral:uuid-123", 300);
  }

  @Test
  void appendUsesBroadcastTtlForBroadcastStreams() {
    OdysseyEvent event =
        OdysseyEvent.builder()
            .eventType("test")
            .payload("data")
            .timestamp(Instant.now())
            .streamKey("odyssey:broadcast:announcements")
            .build();

    when(commands.xadd(eq("odyssey:broadcast:announcements"), any(XAddArgs.class), any(Map.class)))
        .thenReturn("1-0");
    when(commands.expire("odyssey:broadcast:announcements", 86400)).thenReturn(true);

    eventLog.append("odyssey:broadcast:announcements", event);

    verify(commands).expire("odyssey:broadcast:announcements", 86400);
  }

  @Test
  void readAfterReconstructsMetadataFromExtraFields() {
    Map<String, String> body =
        Map.of(
            "eventType", "test",
            "payload", "data",
            "timestamp", "2026-04-06T12:00:00Z",
            "userId", "user-123");
    StreamMessage<String, String> msg = new StreamMessage<>("odyssey:channel:test", "1-0", body);

    when(commands.xread(any(XReadArgs.class), any(XReadArgs.StreamOffset[].class)))
        .thenReturn(List.of(msg));

    List<OdysseyEvent> events = eventLog.readAfter("odyssey:channel:test", "0-0").toList();

    assertThat(events).hasSize(1);
    assertThat(events.getFirst().metadata()).containsEntry("userId", "user-123");
    assertThat(events.getFirst().metadata()).doesNotContainKey("eventType");
    assertThat(events.getFirst().metadata()).doesNotContainKey("payload");
    assertThat(events.getFirst().metadata()).doesNotContainKey("timestamp");
  }
}
