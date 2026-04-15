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
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.SseEventMapper;

class DefaultSubscriberConfigTest {

  private SseEventMapper<String> newMapper() {
    return mock();
  }

  @Test
  void defaultsAreNoops() throws java.io.IOException {
    DefaultSubscriberConfig<String> config = new DefaultSubscriberConfig<>(newMapper());

    // Default timeout and keep-alive are present.
    assertThat(config.timeout()).isEqualTo(Duration.ZERO);
    assertThat(config.keepAliveInterval()).isEqualTo(Duration.ofSeconds(30));

    // Default callbacks do nothing. Invoking them should not throw and should cover
    // the no-op lambdas created in field initializers.
    config.onCompleted().run();
    config.onExpired().run();
    config.onDeleted().run();
    config.onErrored().accept(new RuntimeException("should be swallowed by default noop"));
    config.onCancelled().run();
    config
        .onSubscribe()
        .accept(mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class));
  }

  @Test
  void timeoutSetterReturnsThisAndPersists() {
    DefaultSubscriberConfig<String> config = new DefaultSubscriberConfig<>(newMapper());

    assertThat(config.timeout(Duration.ofMinutes(5))).isSameAs(config);
    assertThat(config.timeout()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void keepAliveIntervalSetterReturnsThisAndPersists() {
    DefaultSubscriberConfig<String> config = new DefaultSubscriberConfig<>(newMapper());

    assertThat(config.keepAliveInterval(Duration.ofSeconds(5))).isSameAs(config);
    assertThat(config.keepAliveInterval()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void mapperSetterReturnsThisAndPersists() {
    SseEventMapper<String> initial = newMapper();
    SseEventMapper<String> replacement = newMapper();
    DefaultSubscriberConfig<String> config = new DefaultSubscriberConfig<>(initial);

    assertThat(config.mapper()).isSameAs(initial);
    assertThat(config.mapper(replacement)).isSameAs(config);
    assertThat(config.mapper()).isSameAs(replacement);
  }

  @Test
  void onCompletedSetterReturnsThisAndPersists() {
    DefaultSubscriberConfig<String> config = new DefaultSubscriberConfig<>(newMapper());
    Runnable action = () -> {};

    assertThat(config.onCompleted(action)).isSameAs(config);
    assertThat(config.onCompleted()).isSameAs(action);
  }

  @Test
  void onExpiredSetterReturnsThisAndPersists() {
    DefaultSubscriberConfig<String> config = new DefaultSubscriberConfig<>(newMapper());
    Runnable action = () -> {};

    assertThat(config.onExpired(action)).isSameAs(config);
    assertThat(config.onExpired()).isSameAs(action);
  }

  @Test
  void onDeletedSetterReturnsThisAndPersists() {
    DefaultSubscriberConfig<String> config = new DefaultSubscriberConfig<>(newMapper());
    Runnable action = () -> {};

    assertThat(config.onDeleted(action)).isSameAs(config);
    assertThat(config.onDeleted()).isSameAs(action);
  }

  @Test
  void onErroredSetterReturnsThisAndPersists() {
    DefaultSubscriberConfig<String> config = new DefaultSubscriberConfig<>(newMapper());
    java.util.function.Consumer<Throwable> action = t -> {};

    assertThat(config.onErrored(action)).isSameAs(config);
    assertThat(config.onErrored()).isSameAs(action);
  }

  @Test
  void onCancelledSetterReturnsThisAndPersists() {
    DefaultSubscriberConfig<String> config = new DefaultSubscriberConfig<>(newMapper());
    Runnable action = () -> {};

    assertThat(config.onCancelled(action)).isSameAs(config);
    assertThat(config.onCancelled()).isSameAs(action);
  }

  @Test
  void onSubscribeSetterReturnsThisAndPersists() {
    DefaultSubscriberConfig<String> config = new DefaultSubscriberConfig<>(newMapper());
    org.jwcarman.odyssey.core.SubscriberConfig.SubscribeHook action = emitter -> {};

    assertThat(config.onSubscribe(action)).isSameAs(config);
    assertThat(config.onSubscribe()).isSameAs(action);
  }
}
