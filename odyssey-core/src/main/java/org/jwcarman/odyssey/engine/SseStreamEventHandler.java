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
    try {
      emitter.send(SseEmitter.event().id(event.id()).name(event.eventType()).data(event.payload()));
    } catch (IOException e) {
      doCleanup();
    }
  }

  @Override
  public void onKeepAlive() {
    try {
      emitter.send(SseEmitter.event().comment("keep-alive"));
    } catch (IOException e) {
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
