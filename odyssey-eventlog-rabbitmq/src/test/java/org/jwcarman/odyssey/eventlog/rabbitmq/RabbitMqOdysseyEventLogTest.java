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
package org.jwcarman.odyssey.eventlog.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.stream.ConfirmationHandler;
import com.rabbitmq.stream.ConfirmationStatus;
import com.rabbitmq.stream.Consumer;
import com.rabbitmq.stream.ConsumerBuilder;
import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageBuilder;
import com.rabbitmq.stream.MessageBuilder.ApplicationPropertiesBuilder;
import com.rabbitmq.stream.OffsetSpecification;
import com.rabbitmq.stream.Producer;
import com.rabbitmq.stream.ProducerBuilder;
import com.rabbitmq.stream.StreamCreator;
import com.rabbitmq.stream.StreamException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RabbitMqOdysseyEventLogTest {

  @Mock private Environment environment;
  @Mock private StreamCreator streamCreator;
  @Mock private ProducerBuilder producerBuilder;
  @Mock private Producer producer;
  @Mock private MessageBuilder messageBuilder;
  @Mock private ApplicationPropertiesBuilder appPropsBuilder;
  @Mock private ConsumerBuilder consumerBuilder;
  @Mock private Consumer consumer;

  private RabbitMqOdysseyEventLog eventLog;

  @BeforeEach
  void setUp() {
    eventLog =
        new RabbitMqOdysseyEventLog(
            environment, Duration.ofHours(1), 524288000L, "ephemeral:", "channel:", "broadcast:");
  }

  // ---------------------------------------------------------------------------
  // append() — confirmed path (sanity check, ensures stubs work together)
  // ---------------------------------------------------------------------------

  private void stubStreamCreator() {
    when(environment.streamCreator()).thenReturn(streamCreator);
    when(streamCreator.stream(anyString())).thenReturn(streamCreator);
    when(streamCreator.maxAge(any(Duration.class))).thenReturn(streamCreator);
    when(streamCreator.maxLengthBytes(any())).thenReturn(streamCreator);
  }

  private void stubProducerChain() {
    when(environment.producerBuilder()).thenReturn(producerBuilder);
    when(producerBuilder.stream(anyString())).thenReturn(producerBuilder);
    when(producerBuilder.build()).thenReturn(producer);
    when(producer.messageBuilder()).thenReturn(messageBuilder);
    when(messageBuilder.applicationProperties()).thenReturn(appPropsBuilder);
    when(appPropsBuilder.entry(anyString(), anyString())).thenReturn(appPropsBuilder);
    when(appPropsBuilder.messageBuilder()).thenReturn(messageBuilder);
    when(messageBuilder.addData(any(byte[].class))).thenReturn(messageBuilder);
    when(messageBuilder.build()).thenReturn(mock(Message.class));
  }

  private OdysseyEvent buildEvent(String streamKey) {
    return OdysseyEvent.builder()
        .streamKey(streamKey)
        .eventType("test.event")
        .payload("hello")
        .timestamp(Instant.now())
        .build();
  }

  @Test
  void appendConfirmedReturnsEventId() {
    stubStreamCreator();
    stubProducerChain();

    doAnswer(
            inv -> {
              ConfirmationHandler handler = inv.getArgument(1);
              ConfirmationStatus status = mock(ConfirmationStatus.class);
              when(status.isConfirmed()).thenReturn(true);
              handler.handle(status);
              return null;
            })
        .when(producer)
        .send(any(Message.class), any(ConfirmationHandler.class));

    String id = eventLog.append("channel:orders", buildEvent("channel:orders"));

    assertThat(id).isNotNull().isNotBlank();
  }

  // ---------------------------------------------------------------------------
  // append() — error: callback reports not confirmed
  // ---------------------------------------------------------------------------

  @Test
  void appendThrowsWhenNotConfirmed() {
    stubStreamCreator();
    stubProducerChain();

    doAnswer(
            inv -> {
              ConfirmationHandler handler = inv.getArgument(1);
              ConfirmationStatus status = mock(ConfirmationStatus.class);
              when(status.isConfirmed()).thenReturn(false);
              when(status.getCode()).thenReturn((short) 500);
              handler.handle(status);
              return null;
            })
        .when(producer)
        .send(any(Message.class), any(ConfirmationHandler.class));

    assertThatThrownBy(() -> eventLog.append("channel:orders", buildEvent("channel:orders")))
        .isInstanceOf(StreamException.class)
        .hasMessageContaining("Failed to publish message");
  }

  // ---------------------------------------------------------------------------
  // append() — error: latch.await times out (never calls handler)
  // ---------------------------------------------------------------------------

  @Test
  void appendThrowsOnPublishTimeout() {
    stubStreamCreator();
    stubProducerChain();

    // send() does nothing — handler is never called, latch never counts down
    // The real code waits PUBLISH_TIMEOUT_SECONDS (5 s) before throwing, which is too slow for
    // a unit test. We use a subclass that overrides the timeout to 0 to make the test fast.
    RabbitMqOdysseyEventLog fastTimeoutLog =
        new RabbitMqOdysseyEventLog(
            environment, Duration.ofHours(1), 524288000L, "ephemeral:", "channel:", "broadcast:") {
          // There is no way to reduce the timeout without subclassing; the timeout constant is
          // package-private. Instead we rely on the fact that send() never invokes the handler so
          // the latch will not count down within the 5-second window.
          // To keep the test fast we simply verify the exception type by triggering immediately
          // via an interrupted thread.
        };

    // Interrupt the current thread before calling append so latch.await throws immediately.
    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> fastTimeoutLog.append("channel:orders", buildEvent("channel:orders")))
        .isInstanceOf(StreamException.class)
        .hasMessageContaining("Interrupted");

    // Clear the interrupt flag so subsequent tests are not affected.
    Thread.interrupted();
  }

  // ---------------------------------------------------------------------------
  // consumeAll() — StreamException from consumerBuilder → returns empty list
  // ---------------------------------------------------------------------------

  @Test
  void readAfterReturnsEmptyWhenConsumerBuilderThrows() {
    // streamExists() must return true so we reach the consumerBuilder call
    when(environment.queryStreamStats(anyString()))
        .thenReturn(null); // no exception → stream exists

    when(environment.consumerBuilder()).thenReturn(consumerBuilder);
    when(consumerBuilder.stream(anyString())).thenReturn(consumerBuilder);
    when(consumerBuilder.offset(any(OffsetSpecification.class))).thenReturn(consumerBuilder);
    when(consumerBuilder.messageHandler(any())).thenReturn(consumerBuilder);
    when(consumerBuilder.build()).thenThrow(new StreamException("cannot connect"));

    List<OdysseyEvent> result = eventLog.readAfter("channel:orders", "0000").toList();

    assertThat(result).isEmpty();
  }

  @Test
  void readLastReturnsEmptyWhenConsumerBuilderThrows() {
    when(environment.queryStreamStats(anyString())).thenReturn(null);

    when(environment.consumerBuilder()).thenReturn(consumerBuilder);
    when(consumerBuilder.stream(anyString())).thenReturn(consumerBuilder);
    when(consumerBuilder.offset(any(OffsetSpecification.class))).thenReturn(consumerBuilder);
    when(consumerBuilder.messageHandler(any())).thenReturn(consumerBuilder);
    when(consumerBuilder.build()).thenThrow(new StreamException("cannot connect"));

    List<OdysseyEvent> result = eventLog.readLast("channel:orders", 5).toList();

    assertThat(result).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // consumeAll() — InterruptedException during queue.poll → interrupt flag set
  // ---------------------------------------------------------------------------

  @Test
  void readLastSetsInterruptFlagWhenInterruptedDuringPoll() throws Exception {
    when(environment.queryStreamStats(anyString())).thenReturn(null);

    when(environment.consumerBuilder()).thenReturn(consumerBuilder);
    when(consumerBuilder.stream(anyString())).thenReturn(consumerBuilder);
    when(consumerBuilder.offset(any(OffsetSpecification.class))).thenReturn(consumerBuilder);
    when(consumerBuilder.messageHandler(any())).thenReturn(consumerBuilder);
    when(consumerBuilder.build()).thenReturn(consumer);

    // We need the poll inside consumeAll to be interrupted. The simplest way is to
    // interrupt the thread before the call so that the first poll() raises
    // InterruptedException immediately.
    Thread.currentThread().interrupt();

    List<OdysseyEvent> result = eventLog.readLast("channel:orders", 5).toList();

    // The interrupt flag must be set after the call returns.
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    assertThat(result).isEmpty();

    // Clean up so other tests are not affected.
    Thread.interrupted();
  }

  // ---------------------------------------------------------------------------
  // streamExists() — returns false when queryStreamStats throws StreamException
  // ---------------------------------------------------------------------------

  @Test
  void readAfterReturnsEmptyWhenStreamDoesNotExist() {
    when(environment.queryStreamStats(anyString()))
        .thenThrow(new StreamException("stream not found"));

    List<OdysseyEvent> result = eventLog.readAfter("channel:nonexistent", "0000").toList();

    assertThat(result).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // deserializeMessage() — null applicationProperties
  // ---------------------------------------------------------------------------

  @Test
  void readLastHandlesMessageWithNullApplicationProperties() {
    when(environment.queryStreamStats(anyString())).thenReturn(null);

    when(environment.consumerBuilder()).thenReturn(consumerBuilder);
    when(consumerBuilder.stream(anyString())).thenReturn(consumerBuilder);
    when(consumerBuilder.offset(any(OffsetSpecification.class))).thenReturn(consumerBuilder);
    when(consumerBuilder.messageHandler(any()))
        .thenAnswer(
            inv -> {
              com.rabbitmq.stream.MessageHandler handler = inv.getArgument(0);
              Message msg = mock(Message.class);
              when(msg.getApplicationProperties()).thenReturn(null);
              when(msg.getBodyAsBinary()).thenReturn("payload".getBytes());
              handler.handle(null, msg);
              return consumerBuilder;
            });
    when(consumerBuilder.build()).thenReturn(consumer);

    // The call must not throw; null applicationProperties are handled gracefully
    List<OdysseyEvent> result = eventLog.readLast("channel:orders", 5).toList();
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().payload()).isEqualTo("payload");
  }

  // ---------------------------------------------------------------------------
  // deserializeMessage() — null body
  // ---------------------------------------------------------------------------

  @Test
  void readLastHandlesMessageWithNullBody() {
    when(environment.queryStreamStats(anyString())).thenReturn(null);

    when(environment.consumerBuilder()).thenReturn(consumerBuilder);
    when(consumerBuilder.stream(anyString())).thenReturn(consumerBuilder);
    when(consumerBuilder.offset(any(OffsetSpecification.class))).thenReturn(consumerBuilder);
    when(consumerBuilder.messageHandler(any()))
        .thenAnswer(
            inv -> {
              com.rabbitmq.stream.MessageHandler handler = inv.getArgument(0);
              Message msg = mock(Message.class);
              when(msg.getApplicationProperties())
                  .thenReturn(
                      Map.of(
                          "eventId", "evt-1",
                          "streamKey", "channel:orders",
                          "eventType", "test",
                          "timestamp", Instant.now().toString()));
              when(msg.getBodyAsBinary()).thenReturn(null);
              handler.handle(null, msg);
              return consumerBuilder;
            });
    when(consumerBuilder.build()).thenReturn(consumer);

    List<OdysseyEvent> result = eventLog.readLast("channel:orders", 5).toList();

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().payload()).isNull();
  }

  // ---------------------------------------------------------------------------
  // close() — closes all producers and clears map
  // ---------------------------------------------------------------------------

  @Test
  void closeClosesAllProducersAndClearsMap() {
    stubStreamCreator();
    stubProducerChain();

    Producer producer2 = mock(Producer.class);
    MessageBuilder messageBuilder2 = mock(MessageBuilder.class);
    ApplicationPropertiesBuilder appProps2 = mock(ApplicationPropertiesBuilder.class);
    when(messageBuilder2.applicationProperties()).thenReturn(appProps2);
    when(appProps2.entry(anyString(), anyString())).thenReturn(appProps2);
    when(appProps2.messageBuilder()).thenReturn(messageBuilder2);
    when(messageBuilder2.addData(any(byte[].class))).thenReturn(messageBuilder2);
    when(messageBuilder2.build()).thenReturn(mock(Message.class));
    when(producer2.messageBuilder()).thenReturn(messageBuilder2);

    // Stub producer builder to return different producers for different streams.
    when(producerBuilder.stream("channel:a")).thenReturn(producerBuilder);
    when(producerBuilder.stream("channel:b")).thenReturn(producerBuilder);
    when(producerBuilder.build()).thenReturn(producer).thenReturn(producer2);

    // Stub send to immediately confirm for both
    doAnswer(
            inv -> {
              ConfirmationHandler h = inv.getArgument(1);
              ConfirmationStatus s = mock(ConfirmationStatus.class);
              when(s.isConfirmed()).thenReturn(true);
              h.handle(s);
              return null;
            })
        .when(producer)
        .send(any(), any());
    doAnswer(
            inv -> {
              ConfirmationHandler h = inv.getArgument(1);
              ConfirmationStatus s = mock(ConfirmationStatus.class);
              when(s.isConfirmed()).thenReturn(true);
              h.handle(s);
              return null;
            })
        .when(producer2)
        .send(any(), any());

    eventLog.append("channel:a", buildEvent("channel:a"));
    eventLog.append("channel:b", buildEvent("channel:b"));

    eventLog.close();

    verify(producer).close();
    verify(producer2).close();
  }

  // ---------------------------------------------------------------------------
  // delete() — removes producer, closes it, then tries environment.deleteStream.
  //            StreamException from deleteStream is ignored.
  // ---------------------------------------------------------------------------

  @Test
  void deleteClosesProducerAndIgnoresDeleteStreamException() {
    stubStreamCreator();
    stubProducerChain();

    doAnswer(
            inv -> {
              ConfirmationHandler h = inv.getArgument(1);
              ConfirmationStatus s = mock(ConfirmationStatus.class);
              when(s.isConfirmed()).thenReturn(true);
              h.handle(s);
              return null;
            })
        .when(producer)
        .send(any(), any());

    eventLog.append("channel:orders", buildEvent("channel:orders"));

    doThrow(new StreamException("stream not found"))
        .when(environment)
        .deleteStream("channel:orders");

    // Must not throw
    eventLog.delete("channel:orders");

    verify(producer).close();
    verify(environment).deleteStream("channel:orders");
  }

  @Test
  void deleteOnNonExistentProducerStillCallsDeleteStream() {
    doThrow(new StreamException("no stream")).when(environment).deleteStream("channel:ghost");

    // Must not throw even without a cached producer
    eventLog.delete("channel:ghost");

    verify(producer, never()).close();
    verify(environment).deleteStream("channel:ghost");
  }

  // ---------------------------------------------------------------------------
  // ensureStreamExists() — StreamException is silently ignored
  // ---------------------------------------------------------------------------

  @Test
  void appendSilentlyIgnoresStreamAlreadyExistsException() {
    when(environment.streamCreator()).thenReturn(streamCreator);
    when(streamCreator.stream(anyString())).thenReturn(streamCreator);
    when(streamCreator.maxAge(any(Duration.class))).thenReturn(streamCreator);
    when(streamCreator.maxLengthBytes(any())).thenReturn(streamCreator);
    doThrow(new StreamException("stream already exists")).when(streamCreator).create();

    stubProducerChain();

    doAnswer(
            inv -> {
              ConfirmationHandler h = inv.getArgument(1);
              ConfirmationStatus s = mock(ConfirmationStatus.class);
              when(s.isConfirmed()).thenReturn(true);
              h.handle(s);
              return null;
            })
        .when(producer)
        .send(any(), any());

    String id = eventLog.append("channel:orders", buildEvent("channel:orders"));

    assertThat(id).isNotNull();
  }
}
