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
package org.jwcarman.odyssey.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.DeliveredEvent;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.odyssey.core.SseEventMapper.TerminalState;
import org.jwcarman.substrate.BlockingSubscription;
import org.jwcarman.substrate.NextResult;
import org.jwcarman.substrate.journal.JournalEntry;
import org.jwcarman.substrate.journal.JournalExpiredException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SseJournalAdapterTest {

  @Mock private BlockingSubscription<JournalEntry<StoredEvent>> source;
  @Mock private SseEmitter emitter;

  private final ObjectMapper objectMapper = new ObjectMapper();

  record TestData(String msg) {}

  private SseJournalAdapter<TestData> newAdapter(DefaultSubscriberConfig<TestData> config) {
    return new SseJournalAdapter<>(
        source, emitter, "test-key", config, objectMapper, TestData.class);
  }

  private DefaultSubscriberConfig<TestData> defaultConfig() {
    DefaultSubscriberConfig<TestData> config =
        new DefaultSubscriberConfig<>(SseEventMapper.defaultMapper(objectMapper));
    config.keepAliveInterval(Duration.ofMillis(100));
    return config;
  }

  @Test
  void sendsEventOnValue() throws Exception {
    StoredEvent stored = new StoredEvent("test.event", "{\"msg\":\"hello\"}", java.util.Map.of());
    JournalEntry<StoredEvent> entry = new JournalEntry<>("id-1", "test-key", stored, Instant.now());

    when(source.isActive()).thenReturn(true, false);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Value<>(entry));

    newAdapter(defaultConfig()).begin();

    verify(emitter, timeout(2000).atLeast(2)).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void sendsKeepAliveOnTimeout() throws Exception {
    when(source.isActive()).thenReturn(true, false);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Timeout<>());

    newAdapter(defaultConfig()).begin();

    verify(emitter, timeout(2000).atLeast(2)).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void emitsTerminalOnCompleted() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    DefaultSubscriberConfig<TestData> config = defaultConfig();
    config.onCompleted(latch::countDown);

    when(source.isActive()).thenReturn(true);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Completed<>());

    newAdapter(config).begin();

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    verify(emitter, timeout(2000)).complete();
  }

  @Test
  void emitsTerminalOnExpired() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    DefaultSubscriberConfig<TestData> config = defaultConfig();
    config.onExpired(latch::countDown);

    when(source.isActive()).thenReturn(true);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Expired<>());

    newAdapter(config).begin();

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    verify(emitter, timeout(2000)).complete();
  }

  @Test
  void emitsTerminalOnDeleted() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    DefaultSubscriberConfig<TestData> config = defaultConfig();
    config.onDeleted(latch::countDown);

    when(source.isActive()).thenReturn(true);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Deleted<>());

    newAdapter(config).begin();

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    verify(emitter, timeout(2000)).complete();
  }

  @Test
  void completesWithErrorOnErroredWhenMapperDoesNotEmitFrame() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> captured = new AtomicReference<>();
    DefaultSubscriberConfig<TestData> config = defaultConfig();
    config.onErrored(
        t -> {
          captured.set(t);
          latch.countDown();
        });

    RuntimeException cause = new RuntimeException("backend failure");
    when(source.isActive()).thenReturn(true);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Errored<>(cause));

    newAdapter(config).begin();

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(captured.get()).isEqualTo(cause);
    verify(emitter, timeout(2000)).completeWithError(cause);
    verify(emitter, never()).complete();
  }

  @Test
  void completesNormallyOnErroredWhenMapperEmitsInBandFrame() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    SseEventMapper<TestData> inBandErrorMapper =
        new SseEventMapper<>() {
          @Override
          public SseEmitter.SseEventBuilder map(DeliveredEvent<TestData> event) {
            return SseEmitter.event().data("");
          }

          @Override
          public Optional<SseEmitter.SseEventBuilder> terminal(TerminalState state) {
            return Optional.of(SseEmitter.event().name("errored").data("in-band"));
          }
        };
    DefaultSubscriberConfig<TestData> config = new DefaultSubscriberConfig<>(inBandErrorMapper);
    config.keepAliveInterval(Duration.ofMillis(100));
    config.onErrored(t -> latch.countDown());

    RuntimeException cause = new RuntimeException("backend failure");
    when(source.isActive()).thenReturn(true);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Errored<>(cause));

    newAdapter(config).begin();

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    verify(emitter, timeout(2000)).complete();
    verify(emitter, never()).completeWithError(any(Throwable.class));
  }

  @Test
  void closesOnSendIOException() throws Exception {
    doThrow(new IOException("broken pipe"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    newAdapter(defaultConfig()).begin();

    verify(source, timeout(2000)).cancel();
    verify(emitter, timeout(2000)).complete();
  }

  @Test
  void closeIsIdempotent() {
    SseJournalAdapter<TestData> adapter = newAdapter(defaultConfig());

    adapter.close();
    adapter.close();
    adapter.close();

    verify(source, times(1)).cancel();
  }

  @Test
  void launchFiresTerminalExpiredWhenSupplierThrows() throws Exception {
    SseEventMapper<TestData> mapper = mock();
    when(mapper.terminal(any(TerminalState.Expired.class))).thenReturn(Optional.empty());

    CountDownLatch onExpiredLatch = new CountDownLatch(1);
    DefaultSubscriberConfig<TestData> config = new DefaultSubscriberConfig<>(mapper);
    config.onExpired(onExpiredLatch::countDown);

    SseJournalAdapter.launch(
        () -> {
          throw new JournalExpiredException("gone");
        },
        emitter,
        "test-key",
        config,
        objectMapper,
        TestData.class);

    verify(mapper).terminal(any(TerminalState.Expired.class));
    verify(emitter).complete();
    verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    assertThat(onExpiredLatch.getCount()).isZero();
  }

  @Test
  void launchSendsTerminalExpiredFrameWhenMapperProvidesOne() throws Exception {
    SseEventMapper<TestData> mapper = mock();
    SseEmitter.SseEventBuilder frame = SseEmitter.event().name("expired");
    when(mapper.terminal(any(TerminalState.Expired.class))).thenReturn(Optional.of(frame));

    DefaultSubscriberConfig<TestData> config = new DefaultSubscriberConfig<>(mapper);

    SseJournalAdapter.launch(
        () -> {
          throw new JournalExpiredException("gone");
        },
        emitter,
        "test-key",
        config,
        objectMapper,
        TestData.class);

    verify(emitter).send(frame);
    verify(emitter).complete();
  }

  @Test
  void launchSuccessPathDelegatesToAdapter() throws Exception {
    BlockingSubscription<JournalEntry<StoredEvent>> liveSource = mock();
    when(liveSource.isActive()).thenReturn(false);

    SseJournalAdapter.launch(
        () -> liveSource, emitter, "test-key", defaultConfig(), objectMapper, TestData.class);

    verify(emitter, timeout(2000)).complete();
  }

  @Test
  void beginWiresOnCompletionCallbackToClose() {
    newAdapter(defaultConfig()).begin();

    ArgumentCaptor<Runnable> onCompletion = ArgumentCaptor.forClass(Runnable.class);
    verify(emitter, timeout(2000)).onCompletion(onCompletion.capture());

    onCompletion.getValue().run();
    verify(source).cancel();
  }

  @Test
  void beginWiresOnErrorCallbackToClose() {
    newAdapter(defaultConfig()).begin();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.function.Consumer<Throwable>> onError =
        ArgumentCaptor.forClass(java.util.function.Consumer.class);
    verify(emitter, timeout(2000)).onError(onError.capture());

    onError.getValue().accept(new RuntimeException("client exploded"));
    verify(source).cancel();
  }

  @Test
  void beginWiresOnTimeoutCallbackToClose() {
    newAdapter(defaultConfig()).begin();

    ArgumentCaptor<Runnable> onTimeout = ArgumentCaptor.forClass(Runnable.class);
    verify(emitter, timeout(2000)).onTimeout(onTimeout.capture());

    onTimeout.getValue().run();
    verify(source).cancel();
  }

  @Test
  void trySendTerminalHandlesMapperException() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    SseEventMapper<TestData> brokenMapper =
        new SseEventMapper<>() {
          @Override
          public SseEmitter.SseEventBuilder map(
              org.jwcarman.odyssey.core.DeliveredEvent<TestData> event) {
            return SseEmitter.event().data("");
          }

          @Override
          public Optional<SseEmitter.SseEventBuilder> terminal(TerminalState state) {
            throw new RuntimeException("mapper is broken");
          }
        };
    DefaultSubscriberConfig<TestData> config = new DefaultSubscriberConfig<>(brokenMapper);
    config.keepAliveInterval(Duration.ofMillis(100));
    config.onCompleted(latch::countDown);

    when(source.isActive()).thenReturn(true);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Completed<>());

    newAdapter(config).begin();

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    verify(emitter, timeout(2000)).complete();
  }

  @Test
  void trySendTerminalHandlesSendIOException() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    SseEmitter.SseEventBuilder terminalFrame = SseEmitter.event().name("done");
    SseEventMapper<TestData> mapperWithFrame =
        new SseEventMapper<>() {
          @Override
          public SseEmitter.SseEventBuilder map(
              org.jwcarman.odyssey.core.DeliveredEvent<TestData> event) {
            return SseEmitter.event().data("");
          }

          @Override
          public Optional<SseEmitter.SseEventBuilder> terminal(TerminalState state) {
            return Optional.of(terminalFrame);
          }
        };
    DefaultSubscriberConfig<TestData> config = new DefaultSubscriberConfig<>(mapperWithFrame);
    config.keepAliveInterval(Duration.ofMillis(100));
    config.onCompleted(latch::countDown);

    when(source.isActive()).thenReturn(true);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Completed<>());
    // First send is the "connected" comment — let it succeed. Second send is the
    // terminal frame — make it throw IOException to exercise sendTerminalFrame's
    // catch block.
    org.mockito.Mockito.doNothing()
        .doThrow(new IOException("pipe broken"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    newAdapter(config).begin();

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    verify(emitter, timeout(2000)).complete();
  }

  @Test
  void launchHandlesMapperExceptionInFireTerminalExpired() {
    SseEventMapper<TestData> brokenMapper =
        new SseEventMapper<>() {
          @Override
          public SseEmitter.SseEventBuilder map(
              org.jwcarman.odyssey.core.DeliveredEvent<TestData> event) {
            return SseEmitter.event();
          }

          @Override
          public Optional<SseEmitter.SseEventBuilder> terminal(TerminalState state) {
            throw new RuntimeException("mapper is broken");
          }
        };
    DefaultSubscriberConfig<TestData> config = new DefaultSubscriberConfig<>(brokenMapper);

    SseJournalAdapter.launch(
        () -> {
          throw new JournalExpiredException("gone");
        },
        emitter,
        "test-key",
        config,
        objectMapper,
        TestData.class);

    verify(emitter).complete();
  }

  @Test
  void launchHandlesIOExceptionWhenSendingExpiredFrame() throws Exception {
    SseEventMapper<TestData> mapperWithFrame =
        new SseEventMapper<>() {
          @Override
          public SseEmitter.SseEventBuilder map(
              org.jwcarman.odyssey.core.DeliveredEvent<TestData> event) {
            return SseEmitter.event();
          }

          @Override
          public Optional<SseEmitter.SseEventBuilder> terminal(TerminalState state) {
            return Optional.of(SseEmitter.event().name("expired"));
          }
        };
    DefaultSubscriberConfig<TestData> config = new DefaultSubscriberConfig<>(mapperWithFrame);
    doThrow(new IOException("pipe broken"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    SseJournalAdapter.launch(
        () -> {
          throw new JournalExpiredException("gone");
        },
        emitter,
        "test-key",
        config,
        objectMapper,
        TestData.class);

    verify(emitter).complete();
  }

  @Test
  void launchHandlesOnExpiredCallbackException() {
    SseEventMapper<TestData> mapper =
        new SseEventMapper<>() {
          @Override
          public SseEmitter.SseEventBuilder map(
              org.jwcarman.odyssey.core.DeliveredEvent<TestData> event) {
            return SseEmitter.event();
          }

          @Override
          public Optional<SseEmitter.SseEventBuilder> terminal(TerminalState state) {
            return Optional.empty();
          }
        };
    DefaultSubscriberConfig<TestData> config = new DefaultSubscriberConfig<>(mapper);
    config.onExpired(
        () -> {
          throw new RuntimeException("callback explodes");
        });

    SseJournalAdapter.launch(
        () -> {
          throw new JournalExpiredException("gone");
        },
        emitter,
        "test-key",
        config,
        objectMapper,
        TestData.class);

    verify(emitter).complete();
  }

  @Test
  void writerLoopCompletesWithErrorOnUnexpectedException() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    DefaultSubscriberConfig<TestData> config = defaultConfig();
    config.onErrored(t -> latch.countDown());

    RuntimeException unexpected = new RuntimeException("subscription exploded");
    when(source.isActive()).thenReturn(true);
    when(source.next(any(Duration.class))).thenThrow(unexpected);

    newAdapter(config).begin();

    verify(emitter, timeout(2000)).completeWithError(unexpected);
    verify(emitter, never()).complete();
  }

  @Test
  void writerLoopHandlesMidStreamJournalExpiredException() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    DefaultSubscriberConfig<TestData> config = defaultConfig();
    config.onExpired(latch::countDown);

    when(source.isActive()).thenReturn(true);
    when(source.next(any(Duration.class)))
        .thenThrow(new JournalExpiredException("expired mid-stream"));

    newAdapter(config).begin();

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    verify(emitter, timeout(2000)).complete();
  }
}
