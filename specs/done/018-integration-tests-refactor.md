# Update integration tests for new module structure

## What to build

Update the integration tests (from spec 010) to work with the new module structure.
Add tests for the in-memory backend and verify that auto-configuration fallback works
correctly.

**Move/update existing integration tests:**
- Tests that were in `odyssey-redis` should move to `odyssey-eventlog-redis` and
  `odyssey-notifier-redis` as appropriate
- Full end-to-end tests (publish → subscribe → receive) can live in a test module or
  in `odyssey-example`

**New test cases:**

1. **In-memory backend auto-configuration:** Start a Spring context with only
   `odyssey-core` on the classpath (no Redis). Verify `InMemoryOdysseyEventLog` and
   `InMemoryOdysseyStreamNotifier` beans are created. Verify WARN log messages are printed.

2. **Redis backend auto-configuration:** Start a Spring context with Redis modules on
   the classpath. Verify `RedisOdysseyEventLog` and `RedisOdysseyStreamNotifier` beans
   are created. Verify in-memory fallbacks do NOT activate.

3. **In-memory end-to-end:** Full publish → subscribe → receive flow using only the
   in-memory backend. No Redis required.

4. **In-memory bounded eviction:** Publish more events than `maxLen`, verify oldest
   events are evicted and `readAfter`/`readLast` return correct results.

5. **Mixed backend (if testable):** e.g., in-memory event log + Redis notifier. Verify
   it works.

## Acceptance criteria

- [ ] Existing Redis integration tests pass in the new module locations
- [ ] In-memory auto-configuration test passes
- [ ] Redis auto-configuration test passes (in-memory does not activate)
- [ ] In-memory end-to-end test passes without any Redis dependency
- [ ] Bounded eviction test passes
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Use `@SpringBootTest` with selective auto-configuration or `ApplicationContextRunner`
  for auto-configuration tests.
- In-memory tests should be fast — no Testcontainers needed.
