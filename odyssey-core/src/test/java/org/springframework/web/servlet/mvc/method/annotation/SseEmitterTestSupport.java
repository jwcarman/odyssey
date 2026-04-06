package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.http.MediaType;

/**
 * Test helper that installs a capturing {@link ResponseBodyEmitter.Handler} on an {@link
 * SseEmitter}, allowing tests to observe SSE events without a servlet container.
 *
 * <p>Lives in Spring's package to access the package-private {@code Handler} interface and {@code
 * initialize()} method.
 */
public final class SseEmitterTestSupport {

  private SseEmitterTestSupport() {}

  public record CapturedSseEvent(String id, String eventType, String data, String comment) {
    public boolean isKeepAlive() {
      return comment != null && comment.contains("keep-alive");
    }
  }

  public static final class SseCapture {

    private final BlockingQueue<CapturedSseEvent> events = new LinkedBlockingQueue<>();
    private final CountDownLatch completionLatch = new CountDownLatch(1);
    private volatile Throwable error;

    public CapturedSseEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
      return events.poll(timeout, unit);
    }

    public List<CapturedSseEvent> pollEvents(int count, long timeout, TimeUnit unit)
        throws InterruptedException {
      List<CapturedSseEvent> result = new ArrayList<>();
      long deadline = System.nanoTime() + unit.toNanos(timeout);
      for (int i = 0; i < count; i++) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) break;
        CapturedSseEvent event = events.poll(remaining, TimeUnit.NANOSECONDS);
        if (event == null) break;
        result.add(event);
      }
      return result;
    }

    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
      return completionLatch.await(timeout, unit);
    }

    public Throwable getError() {
      return error;
    }

    public int eventCount() {
      return events.size();
    }

    void handleSendSet(Set<ResponseBodyEmitter.DataWithMediaType> items) {
      StringBuilder sb = new StringBuilder();
      for (ResponseBodyEmitter.DataWithMediaType item : items) {
        Object data = item.getData();
        if (data instanceof String s) {
          sb.append(s);
        }
      }
      parseAndEnqueue(sb.toString());
    }

    void handleComplete(Runnable completionCallback) {
      if (completionCallback != null) {
        completionCallback.run();
      }
      completionLatch.countDown();
    }

    void handleError(Throwable failure, Consumer<Throwable> errorCallback) {
      error = failure;
      if (errorCallback != null) {
        errorCallback.accept(failure);
      }
      completionLatch.countDown();
    }

    private void parseAndEnqueue(String raw) {
      String[] segments = raw.split("\n\n");
      for (String segment : segments) {
        if (segment.isBlank()) continue;
        events.add(parseSegment(segment));
      }
    }

    private CapturedSseEvent parseSegment(String segment) {
      String id = null;
      String eventType = null;
      String comment = null;
      StringBuilder data = null;
      for (String line : segment.split("\n")) {
        if (line.startsWith("id:")) {
          id = line.substring(3);
        } else if (line.startsWith("event:")) {
          eventType = line.substring(6);
        } else if (line.startsWith("data:")) {
          if (data == null) {
            data = new StringBuilder();
          } else {
            data.append("\n");
          }
          data.append(line.substring(5));
        } else if (line.startsWith(":")) {
          comment = line.substring(1);
        }
      }
      return new CapturedSseEvent(id, eventType, data != null ? data.toString() : null, comment);
    }
  }

  /**
   * Installs a capturing handler on the given emitter. Any events already buffered (before handler
   * installation) are flushed through the handler. Must be called before any significant delay to
   * avoid missing events.
   */
  public static SseCapture capture(SseEmitter emitter) throws IOException {
    SseCapture capture = new SseCapture();

    ResponseBodyEmitter.Handler handler =
        new ResponseBodyEmitter.Handler() {
          private Runnable completionCallback;
          private Consumer<Throwable> errorCallback;

          @Override
          public void send(Object data, MediaType mediaType) throws IOException {
            // Individual item send — used during early-send-attempt flush.
            // Items arrive one-by-one rather than as a grouped set, so we wrap each in a set
            // to reuse the same parsing path.
            handleSendSet(
                Set.of(new ResponseBodyEmitter.DataWithMediaType(data, mediaType)), capture);
          }

          @Override
          public void send(Set<ResponseBodyEmitter.DataWithMediaType> items) throws IOException {
            handleSendSet(items, capture);
          }

          @Override
          public void complete() {
            capture.handleComplete(completionCallback);
          }

          @Override
          public void completeWithError(Throwable failure) {
            capture.handleError(failure, errorCallback);
          }

          @Override
          public void onTimeout(Runnable callback) {}

          @Override
          public void onError(Consumer<Throwable> callback) {
            this.errorCallback = callback;
          }

          @Override
          public void onCompletion(Runnable callback) {
            this.completionCallback = callback;
          }

          private void handleSendSet(
              Set<ResponseBodyEmitter.DataWithMediaType> items, SseCapture cap) {
            cap.handleSendSet(items);
          }
        };

    emitter.initialize(handler);
    return capture;
  }
}
