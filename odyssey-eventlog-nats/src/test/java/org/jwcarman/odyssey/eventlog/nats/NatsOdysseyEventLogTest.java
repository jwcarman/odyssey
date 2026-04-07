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
package org.jwcarman.odyssey.eventlog.nats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.StreamInfo;
import io.nats.client.api.StreamState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NatsOdysseyEventLogTest {

  private static final String STREAM_NAME = "ODYSSEY_TEST";
  private static final Duration MAX_AGE = Duration.ofHours(1);
  private static final long MAX_MESSAGES = 100_000L;

  @Mock private Connection connection;
  @Mock private JetStream jetStream;
  @Mock private JetStreamManagement jsm;
  @Mock private StreamInfo streamInfo;
  @Mock private StreamState streamState;

  // -----------------------------------------------------------------------
  // Helper to build a fully wired Connection mock suitable for construction
  // -----------------------------------------------------------------------

  private void wireConnectionForConstruction() throws IOException, JetStreamApiException {
    when(connection.jetStream()).thenReturn(jetStream);
    when(connection.jetStreamManagement()).thenReturn(jsm);
    when(jsm.addStream(any())).thenReturn(streamInfo);
  }

  private NatsOdysseyEventLog buildEventLog() throws IOException, JetStreamApiException {
    wireConnectionForConstruction();
    return new NatsOdysseyEventLog(
        connection, STREAM_NAME, MAX_AGE, MAX_MESSAGES, "ephemeral:", "channel:", "broadcast:");
  }

  /**
   * Creates a mocked JetStreamApiException with the given API error code. Uses lenient() so the
   * stub is not flagged as unnecessary when the exception is thrown but its code is not inspected.
   */
  private static JetStreamApiException mockApiException(int apiErrorCode) {
    JetStreamApiException ex = mock(JetStreamApiException.class);
    lenient().when(ex.getApiErrorCode()).thenReturn(apiErrorCode);
    return ex;
  }

  // -----------------------------------------------------------------------
  // Constructor tests
  // -----------------------------------------------------------------------

  @Test
  void constructorSucceedsWhenAddStreamSucceeds() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();
    assertThat(eventLog).isNotNull();
  }

  @Test
  void constructorCallsUpdateStreamWhenAddStreamThrowsCode10058()
      throws IOException, JetStreamApiException {
    when(connection.jetStream()).thenReturn(jetStream);
    when(connection.jetStreamManagement()).thenReturn(jsm);
    JetStreamApiException alreadyExists = mockApiException(10058);
    when(jsm.addStream(any())).thenThrow(alreadyExists);
    when(jsm.updateStream(any())).thenReturn(streamInfo);

    NatsOdysseyEventLog eventLog =
        new NatsOdysseyEventLog(
            connection, STREAM_NAME, MAX_AGE, MAX_MESSAGES, "ephemeral:", "channel:", "broadcast:");

    assertThat(eventLog).isNotNull();
    verify(jsm).updateStream(any());
  }

  @Test
  void constructorThrowsUncheckedIOExceptionWhenUpdateStreamThrowsIOException()
      throws IOException, JetStreamApiException {
    when(connection.jetStream()).thenReturn(jetStream);
    when(connection.jetStreamManagement()).thenReturn(jsm);
    JetStreamApiException alreadyExists = mockApiException(10058);
    when(jsm.addStream(any())).thenThrow(alreadyExists);
    when(jsm.updateStream(any())).thenThrow(new IOException("update failed"));

    assertThatThrownBy(
            () ->
                new NatsOdysseyEventLog(
                    connection,
                    STREAM_NAME,
                    MAX_AGE,
                    MAX_MESSAGES,
                    "ephemeral:",
                    "channel:",
                    "broadcast:"))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("update");
  }

  @Test
  void constructorThrowsIllegalStateExceptionWhenUpdateStreamThrowsJetStreamApiException()
      throws IOException, JetStreamApiException {
    when(connection.jetStream()).thenReturn(jetStream);
    when(connection.jetStreamManagement()).thenReturn(jsm);
    JetStreamApiException alreadyExists = mockApiException(10058);
    when(jsm.addStream(any())).thenThrow(alreadyExists);
    JetStreamApiException updateFailed = mockApiException(999);
    when(jsm.updateStream(any())).thenThrow(updateFailed);

    assertThatThrownBy(
            () ->
                new NatsOdysseyEventLog(
                    connection,
                    STREAM_NAME,
                    MAX_AGE,
                    MAX_MESSAGES,
                    "ephemeral:",
                    "channel:",
                    "broadcast:"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("update");
  }

  @Test
  void constructorThrowsIllegalStateExceptionWhenAddStreamThrowsNon10058Code()
      throws IOException, JetStreamApiException {
    when(connection.jetStream()).thenReturn(jetStream);
    when(connection.jetStreamManagement()).thenReturn(jsm);
    JetStreamApiException otherError = mockApiException(500);
    when(jsm.addStream(any())).thenThrow(otherError);

    assertThatThrownBy(
            () ->
                new NatsOdysseyEventLog(
                    connection,
                    STREAM_NAME,
                    MAX_AGE,
                    MAX_MESSAGES,
                    "ephemeral:",
                    "channel:",
                    "broadcast:"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("create");
  }

  @Test
  void constructorThrowsUncheckedIOExceptionWhenAddStreamThrowsIOException()
      throws IOException, JetStreamApiException {
    when(connection.jetStream()).thenReturn(jetStream);
    when(connection.jetStreamManagement()).thenReturn(jsm);
    when(jsm.addStream(any())).thenThrow(new IOException("stream create failed"));

    assertThatThrownBy(
            () ->
                new NatsOdysseyEventLog(
                    connection,
                    STREAM_NAME,
                    MAX_AGE,
                    MAX_MESSAGES,
                    "ephemeral:",
                    "channel:",
                    "broadcast:"))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("create");
  }

  @Test
  void constructorThrowsUncheckedIOExceptionWhenJetStreamThrowsIOException() throws IOException {
    when(connection.jetStream()).thenThrow(new IOException("jetstream init failed"));

    assertThatThrownBy(
            () ->
                new NatsOdysseyEventLog(
                    connection,
                    STREAM_NAME,
                    MAX_AGE,
                    MAX_MESSAGES,
                    "ephemeral:",
                    "channel:",
                    "broadcast:"))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("initialize");
  }

  // -----------------------------------------------------------------------
  // append tests
  // -----------------------------------------------------------------------

  @Test
  void appendThrowsUncheckedIOExceptionWhenPublishThrowsIOException() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();
    when(jetStream.publish(any(Message.class))).thenThrow(new IOException("publish failed"));

    OdysseyEvent event =
        OdysseyEvent.builder()
            .streamKey("channel:orders")
            .eventType("order.created")
            .payload("payload")
            .timestamp(Instant.now())
            .build();

    assertThatThrownBy(() -> eventLog.append("channel:orders", event))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("publish");
  }

  @Test
  void appendThrowsIllegalStateExceptionWhenPublishThrowsJetStreamApiException() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();
    JetStreamApiException publishFailed = mockApiException(999);
    when(jetStream.publish(any(Message.class))).thenThrow(publishFailed);

    OdysseyEvent event =
        OdysseyEvent.builder()
            .streamKey("channel:orders")
            .eventType("order.created")
            .payload("payload")
            .timestamp(Instant.now())
            .build();

    assertThatThrownBy(() -> eventLog.append("channel:orders", event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("publish");
  }

  @Test
  void appendHandlesNullPayload() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();
    io.nats.client.api.PublishAck ack = mock(io.nats.client.api.PublishAck.class);
    when(ack.getSeqno()).thenReturn(1L);
    when(jetStream.publish(any(Message.class))).thenReturn(ack);

    OdysseyEvent event =
        OdysseyEvent.builder()
            .streamKey("channel:orders")
            .eventType("order.created")
            .payload(null)
            .timestamp(Instant.now())
            .build();

    String id = eventLog.append("channel:orders", event);
    assertThat(id).isEqualTo("1");
  }

  @Test
  void appendHandlesNullMetadata() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();
    io.nats.client.api.PublishAck ack = mock(io.nats.client.api.PublishAck.class);
    when(ack.getSeqno()).thenReturn(2L);
    when(jetStream.publish(any(Message.class))).thenReturn(ack);

    OdysseyEvent event =
        OdysseyEvent.builder()
            .streamKey("channel:orders")
            .eventType("order.created")
            .payload("data")
            .timestamp(Instant.now())
            .metadata(null)
            .build();

    String id = eventLog.append("channel:orders", event);
    assertThat(id).isEqualTo("2");
  }

  @Test
  void appendHandlesEmptyMetadata() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();
    io.nats.client.api.PublishAck ack = mock(io.nats.client.api.PublishAck.class);
    when(ack.getSeqno()).thenReturn(3L);
    when(jetStream.publish(any(Message.class))).thenReturn(ack);

    OdysseyEvent event =
        OdysseyEvent.builder()
            .streamKey("channel:orders")
            .eventType("order.created")
            .payload("data")
            .timestamp(Instant.now())
            .metadata(Map.of())
            .build();

    String id = eventLog.append("channel:orders", event);
    assertThat(id).isEqualTo("3");
  }

  // -----------------------------------------------------------------------
  // readAfter tests
  // -----------------------------------------------------------------------

  @Test
  void readAfterReturnsEmptyStreamWhenStartSeqExceedsLastSeq() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    when(jsm.getStreamInfo(STREAM_NAME)).thenReturn(streamInfo);
    when(streamInfo.getStreamState()).thenReturn(streamState);
    when(streamState.getLastSequence()).thenReturn(5L);

    // lastId = "5", so startSeq = 6, which > lastSeq(5) => empty
    Stream<OdysseyEvent> result = eventLog.readAfter("channel:orders", "5");

    assertThat(result.toList()).isEmpty();
  }

  @Test
  void readAfterReturnsEmptyStreamOnIOException() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    when(jsm.getStreamInfo(STREAM_NAME)).thenThrow(new IOException("stream info failed"));

    Stream<OdysseyEvent> result = eventLog.readAfter("channel:orders", "0");

    assertThat(result.toList()).isEmpty();
  }

  @Test
  void readAfterReturnsEmptyStreamOnJetStreamApiException() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    JetStreamApiException ex = mockApiException(404);
    when(jsm.getStreamInfo(STREAM_NAME)).thenThrow(ex);

    Stream<OdysseyEvent> result = eventLog.readAfter("channel:orders", "0");

    assertThat(result.toList()).isEmpty();
  }

  // -----------------------------------------------------------------------
  // readLast tests
  // -----------------------------------------------------------------------

  @Test
  void readLastReturnsEmptyStreamWhenSubjectMessageCountIsZero() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    when(jsm.getStreamInfo(any(String.class), any())).thenReturn(streamInfo);
    when(streamInfo.getStreamState()).thenReturn(streamState);
    when(streamState.getSubjects()).thenReturn(Collections.emptyList());

    Stream<OdysseyEvent> result = eventLog.readLast("channel:orders", 5);

    assertThat(result.toList()).isEmpty();
  }

  @Test
  void readLastReturnsEmptyStreamWhenSubjectsListIsNull() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    when(jsm.getStreamInfo(any(String.class), any())).thenReturn(streamInfo);
    when(streamInfo.getStreamState()).thenReturn(streamState);
    when(streamState.getSubjects()).thenReturn(null);

    Stream<OdysseyEvent> result = eventLog.readLast("channel:orders", 5);

    assertThat(result.toList()).isEmpty();
  }

  @Test
  void readLastReturnsEmptyStreamOnIOException() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    when(jsm.getStreamInfo(any(String.class), any())).thenThrow(new IOException("stream error"));

    Stream<OdysseyEvent> result = eventLog.readLast("channel:orders", 5);

    assertThat(result.toList()).isEmpty();
  }

  @Test
  void readLastReturnsEmptyStreamOnJetStreamApiException() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    JetStreamApiException ex = mockApiException(404);
    when(jsm.getStreamInfo(any(String.class), any())).thenThrow(ex);

    Stream<OdysseyEvent> result = eventLog.readLast("channel:orders", 5);

    assertThat(result.toList()).isEmpty();
  }

  // -----------------------------------------------------------------------
  // delete tests
  // -----------------------------------------------------------------------

  @Test
  void deleteIgnoresIOException() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    doThrow(new IOException("purge failed")).when(jsm).purgeStream(any(), any());

    // Should not throw
    eventLog.delete("channel:orders");
  }

  @Test
  void deleteIgnoresJetStreamApiException() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    doThrow(mockApiException(404)).when(jsm).purgeStream(any(), any());

    // Should not throw
    eventLog.delete("channel:orders");
  }

  // -----------------------------------------------------------------------
  // fetchMessages tests (via readAfter path — subscribe throws)
  // -----------------------------------------------------------------------

  @Test
  void fetchMessagesReturnsEmptyStreamOnIOExceptionDuringSubscribe() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    when(jsm.getStreamInfo(STREAM_NAME)).thenReturn(streamInfo);
    when(streamInfo.getStreamState()).thenReturn(streamState);
    when(streamState.getLastSequence()).thenReturn(10L);
    when(jetStream.subscribe(any(String.class), any(PullSubscribeOptions.class)))
        .thenThrow(new IOException("subscribe failed"));

    Stream<OdysseyEvent> result = eventLog.readAfter("channel:orders", "0");

    assertThat(result.toList()).isEmpty();
  }

  @Test
  void fetchMessagesReturnsEmptyStreamOnJetStreamApiExceptionDuringSubscribe() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    when(jsm.getStreamInfo(STREAM_NAME)).thenReturn(streamInfo);
    when(streamInfo.getStreamState()).thenReturn(streamState);
    when(streamState.getLastSequence()).thenReturn(10L);
    JetStreamApiException ex = mockApiException(500);
    when(jetStream.subscribe(any(String.class), any(PullSubscribeOptions.class))).thenThrow(ex);

    Stream<OdysseyEvent> result = eventLog.readAfter("channel:orders", "0");

    assertThat(result.toList()).isEmpty();
  }

  // -----------------------------------------------------------------------
  // fetchMessages returns empty batch — exercises unsubscribe path
  // -----------------------------------------------------------------------

  @Test
  void fetchMessagesReturnsEmptyStreamWhenBatchIsEmpty() throws Exception {
    NatsOdysseyEventLog eventLog = buildEventLog();

    when(jsm.getStreamInfo(STREAM_NAME)).thenReturn(streamInfo);
    when(streamInfo.getStreamState()).thenReturn(streamState);
    when(streamState.getLastSequence()).thenReturn(10L);

    JetStreamSubscription sub = mock(JetStreamSubscription.class);
    when(jetStream.subscribe(any(String.class), any(PullSubscribeOptions.class))).thenReturn(sub);
    when(sub.fetch(1000, Duration.ofMillis(500))).thenReturn(Collections.emptyList());

    Stream<OdysseyEvent> result = eventLog.readAfter("channel:orders", "0");

    assertThat(result.toList()).isEmpty();
    verify(sub).unsubscribe();
  }
}
