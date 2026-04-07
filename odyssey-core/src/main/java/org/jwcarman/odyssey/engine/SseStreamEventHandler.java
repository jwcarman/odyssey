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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jwcarman.odyssey.core.OdysseyEvent;
import org.jwcarman.odyssey.core.StreamEventHandler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseStreamEventHandler implements StreamEventHandler {

  private final SseEmitter emitter;
  private final Runnable cleanup;
  private final AtomicBoolean cleanedUp = new AtomicBoolean(false);

  SseStreamEventHandler(SseEmitter emitter, Runnable cleanup) {
    this.emitter = emitter;
    this.cleanup = cleanup;
    emitter.onCompletion(this::doCleanup);
    emitter.onError(e -> doCleanup());
    emitter.onTimeout(this::doCleanup);
  }

  @Override
  public void onEvent(OdysseyEvent event) {
    SseEmitter.SseEventBuilder builder = SseEmitter.event().id(event.id()).data(event.payload());
    if (event.eventType() != null) {
      builder.name(event.eventType());
    }
    send(builder);
  }

  @Override
  public void onKeepAlive() {
    send(SseEmitter.event().comment("keep-alive"));
  }

  private void send(SseEmitter.SseEventBuilder event) {
    try {
      emitter.send(event);
    } catch (IOException _) {
      doCleanup();
    }
  }

  @Override
  public void onComplete() {
    emitter.complete();
  }

  @Override
  public void onError(Exception e) {
    emitter.completeWithError(e);
  }

  private void doCleanup() {
    if (cleanedUp.compareAndSet(false, true)) {
      cleanup.run();
    }
  }
}
