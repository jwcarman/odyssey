# Integration tests with Testcontainers

## What to build

Add integration tests in `odyssey-redis` that verify the full publish/subscribe flow
against a real Redis instance using Testcontainers.

**Test infrastructure:**
- Add Testcontainers Redis dependency to `odyssey-redis` (test scope)
- Create a shared base test class or `@Testcontainers` setup that starts a Redis container
  and configures Lettuce connections

**Test cases:**

1. **Publish and subscribe (live):** Subscribe to a stream, publish an event, verify the
   subscriber receives it via the SseEmitter.

2. **Multiple subscribers:** Two subscribers on the same stream both receive the same
   published event.

3. **Resume after disconnect:** Publish 3 events, subscribe with `resumeAfter` using the
   first event's ID, verify only events 2 and 3 are received, then verify live events
   continue.

4. **Replay last N:** Publish 5 events, subscribe with `replayLast(3)`, verify the last 3
   are received in order, then verify live events continue.

5. **Ephemeral close:** Publish events, close the stream, verify the subscriber receives
   all events and then the SseEmitter completes.

6. **Delete:** Subscribe, delete the stream, verify the subscriber is disconnected
   immediately.

7. **Keep-alive:** Subscribe with a short keep-alive interval (1 second), verify a
   keep-alive comment is sent when no events arrive within the interval.

8. **Cross-type consistency:** Verify that ephemeral, channel, and broadcast streams all
   behave identically for the basic publish/subscribe flow.

## Acceptance criteria

- [ ] Testcontainers Redis starts automatically during test execution
- [ ] All 8 test scenarios pass
- [ ] Tests do not require a pre-existing Redis instance
- [ ] Tests run in `./mvnw verify`
- [ ] Tests are not flaky — use appropriate timeouts and `CountDownLatch`/`CompletableFuture`
      for async assertions
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Use `GenericContainer` with `redis:7` or Testcontainers' `RedisContainer` if available.
- To capture SseEmitter output in tests, consider wrapping or using a test helper that
  collects sent events.
- Keep-alive test needs a short interval (1s) to avoid slow tests.
- Use `@Timeout` on tests to fail fast if something hangs.
