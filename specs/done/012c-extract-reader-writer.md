# Extract StreamReader and StreamWriter from StreamSubscriber

## What to build

Split `StreamSubscriber` into three classes, each with a single responsibility:

### `StreamReader` (package-private, `Runnable`)

The reader thread loop. Owns `lastReadId`. Reads from the event log and offers events
to the queue.

```java
class StreamReader implements Runnable {
    private final OdysseyEventLog eventLog;
    private final String streamKey;
    private final Semaphore nudge;
    private final BlockingQueue<OdysseyEvent> queue;
    private final Duration keepAliveInterval;
    private String lastReadId;

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            nudge.tryAcquire(keepAliveInterval.toMillis(), MILLISECONDS);
            nudge.drainPermits();
            eventLog.readAfter(streamKey, lastReadId).forEach(event -> {
                queue.offer(event);
                lastReadId = event.id();
            });
        }
    }
}
```

- `lastReadId` is only ever touched by this thread — no concurrency concerns
- Interrupted → exits cleanly
- Testable in isolation: give it a stub `OdysseyEventLog`, a real `Semaphore`, a real
  `BlockingQueue`, assert events appear in the queue

### `StreamWriter` (package-private, `Runnable`)

The writer thread loop. Polls the queue and delegates to a `StreamEventHandler`.

```java
class StreamWriter implements Runnable {
    private final BlockingQueue<OdysseyEvent> queue;
    private final StreamEventHandler handler;
    private final Duration keepAliveInterval;

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            OdysseyEvent event = queue.poll(keepAliveInterval.toMillis(), MILLISECONDS);
            if (event == POISON) {
                handler.onComplete();
                return;
            } else if (event == null) {
                handler.onKeepAlive();
            } else {
                handler.onEvent(event);
            }
        }
    }
}
```

- Knows nothing about `SseEmitter`, Redis, or event logs
- Testable in isolation: give it a real queue and a mock `StreamEventHandler`, assert
  the right callbacks fire

### `StreamSubscriber` (orchestrator)

Creates the reader, writer, queue, and threads. Manages lifecycle.

```java
class StreamSubscriber {
    private final StreamReader reader;
    private final StreamWriter writer;
    private final BlockingQueue<OdysseyEvent> queue;
    private final Semaphore nudge;
    private Thread readerThread;
    private Thread writerThread;

    void start();                // launch both virtual threads
    void nudge();                // nudge.release()
    void closeGracefully();      // interrupt reader, poison queue
    void closeImmediately();     // interrupt both threads
}
```

- No loop logic — just wiring and lifecycle
- Testable by verifying thread start/stop and that nudge reaches the reader

## Acceptance criteria

- [ ] `StreamReader` class exists, package-private, implements `Runnable`
- [ ] `StreamReader` owns `lastReadId`, calls `eventLog.readAfter()`, offers to queue
- [ ] `StreamWriter` class exists, package-private, implements `Runnable`
- [ ] `StreamWriter` polls queue, calls `StreamEventHandler` methods
- [ ] `StreamWriter` handles POISON → `onComplete()`, null → `onKeepAlive()`,
      event → `onEvent()`
- [ ] `StreamSubscriber` creates reader, writer, queue, and manages thread lifecycle
- [ ] `StreamSubscriber` has no loop logic — just orchestration
- [ ] `closeGracefully()` interrupts reader, poisons queue, writer drains and completes
- [ ] `closeImmediately()` interrupts both threads
- [ ] Unit tests for `StreamReader` in isolation (stub event log, assert queue contents)
- [ ] Unit tests for `StreamWriter` in isolation (mock handler, assert callbacks)
- [ ] Unit tests for `StreamSubscriber` lifecycle (start, nudge, graceful/immediate shutdown)
- [ ] Delete `SseEmitterTestSupport` if no longer needed
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- All three classes live in `org.jwcarman.odyssey.engine` (package-private).
- The POISON sentinel should be a static constant accessible to both `StreamSubscriber`
  (who offers it) and `StreamWriter` (who checks for it). Define it on `StreamSubscriber`
  or in a shared constants location within the package.
- Error handling in `StreamWriter`: if `handler.onEvent()` throws, the writer should
  call `handler.onError(e)` and exit. The cleanup callback on the handler will trigger
  subscriber cleanup.
- `StreamReader`'s `run()` method needs proper `InterruptedException` handling on the
  `tryAcquire` — catch and exit.
