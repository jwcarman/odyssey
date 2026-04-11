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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.odyssey.core.SseEventMapper.TerminalState;
import org.jwcarman.substrate.BlockingSubscription;
import org.jwcarman.substrate.NextResult;
import org.jwcarman.substrate.journal.JournalEntry;
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

  @BeforeEach
  void setUp() throws Exception {}

  private SseJournalAdapter<TestData> createAdapter(DefaultSubscriberConfig<TestData> config) {
    return new SseJournalAdapter<>(
        () -> source, emitter, "test-key", config, objectMapper, TestData.class);
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

    SseJournalAdapter<TestData> adapter = createAdapter(defaultConfig());
    adapter.start();

    verify(emitter, timeout(2000).atLeast(2)).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void sendsKeepAliveOnTimeout() throws Exception {
    when(source.isActive()).thenReturn(true, false);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Timeout<>());

    SseJournalAdapter<TestData> adapter = createAdapter(defaultConfig());
    adapter.start();

    verify(emitter, timeout(2000).atLeast(2)).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void emitsTerminalOnCompleted() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    DefaultSubscriberConfig<TestData> config = defaultConfig();
    config.onCompleted(latch::countDown);

    when(source.isActive()).thenReturn(true);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Completed<>());

    SseJournalAdapter<TestData> adapter = createAdapter(config);
    adapter.start();

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

    SseJournalAdapter<TestData> adapter = createAdapter(config);
    adapter.start();

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

    SseJournalAdapter<TestData> adapter = createAdapter(config);
    adapter.start();

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

    SseJournalAdapter<TestData> adapter = createAdapter(config);
    adapter.start();

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
          public SseEmitter.SseEventBuilder map(
              org.jwcarman.odyssey.core.DeliveredEvent<TestData> event) {
            return SseEmitter.event().data("");
          }

          @Override
          public java.util.Optional<SseEmitter.SseEventBuilder> terminal(TerminalState state) {
            return java.util.Optional.of(SseEmitter.event().name("errored").data("in-band"));
          }
        };
    DefaultSubscriberConfig<TestData> config = new DefaultSubscriberConfig<>(inBandErrorMapper);
    config.keepAliveInterval(Duration.ofMillis(100));
    config.onErrored(t -> latch.countDown());

    RuntimeException cause = new RuntimeException("backend failure");
    when(source.isActive()).thenReturn(true);
    when(source.next(any(Duration.class))).thenReturn(new NextResult.Errored<>(cause));

    SseJournalAdapter<TestData> adapter = createAdapter(config);
    adapter.start();

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    verify(emitter, timeout(2000)).complete();
    verify(emitter, never()).completeWithError(any(Throwable.class));
  }

  @Test
  void closesOnSendIOException() throws Exception {
    doThrow(new IOException("broken pipe"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    SseJournalAdapter<TestData> adapter = createAdapter(defaultConfig());
    adapter.start();

    verify(source, timeout(2000)).cancel();
    verify(emitter, timeout(2000)).complete();
  }

  @Test
  void closeIsIdempotent() {
    AtomicBoolean cancelled = new AtomicBoolean(false);

    SseJournalAdapter<TestData> adapter = createAdapter(defaultConfig());
    // Set source field via reflection-free approach: just call close without start
    // The source is null, so close should not throw
    adapter.close();
    adapter.close();
    // No exception = idempotent
  }
}
