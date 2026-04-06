# Move engine machinery to odyssey-core

## What to build

Move `SubscriberOutbox`, `TopicFanout`, and the stream/registry implementations from
`odyssey-redis` into `odyssey-core` under `org.jwcarman.odyssey.engine`. Refactor them to
program against the SPIs (`OdysseyEventLog`, `OdysseyStreamNotifier`) instead of calling
Lettuce/Redis directly.

**Classes to move and refactor:**

- `SubscriberOutbox` → `org.jwcarman.odyssey.engine.SubscriberOutbox`
  - Reader thread calls `eventLog.readAfter(streamKey, lastReadId)` instead of Redis XREAD
  - No other changes — semaphore, queue, writer thread stay the same

- `TopicFanout` → `org.jwcarman.odyssey.engine.TopicFanout`
  - No changes needed — it's already backend-agnostic

- `RedisOdysseyStream` → `DefaultOdysseyStream` at `org.jwcarman.odyssey.engine`
  - `publish`: calls `eventLog.append()` then `notifier.notify()`
  - `subscribe`/`resumeAfter`/`replayLast`: use `eventLog.readAfter()`/`readLast()` for
    replay, then create outbox wired to the event log
  - `close`/`delete`: shutdown fanout. `delete` also removes the stream from the event log
    (add `void delete(String streamKey)` to `OdysseyEventLog` if needed)

- `RedisOdysseyStreamRegistry` → `DefaultOdysseyStreamRegistry` at `org.jwcarman.odyssey.engine`
  - Takes `OdysseyEventLog` and `OdysseyStreamNotifier` as constructor args
  - Stream caching, ephemeral UUID generation stay the same
  - Subscribes to notifications via `notifier.subscribe()` and dispatches to `TopicFanout`

**After this spec:**
- `odyssey-redis` should be empty or deleted
- All engine logic lives in `odyssey-core`
- Existing tests should be updated to use the new package/class names

## Acceptance criteria

- [ ] `SubscriberOutbox` lives in `org.jwcarman.odyssey.engine`
- [ ] `TopicFanout` lives in `org.jwcarman.odyssey.engine`
- [ ] `DefaultOdysseyStream` implements `OdysseyStream` using the SPIs
- [ ] `DefaultOdysseyStreamRegistry` implements `OdysseyStreamRegistry` using the SPIs
- [ ] No direct Redis/Lettuce imports in `odyssey-core`
- [ ] Notification dispatch (formerly `PubSubNotificationListener`) is handled by the
      registry subscribing via `notifier.subscribe()`
- [ ] All existing unit tests pass (updated for new packages)
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- This is a refactor — behavior should not change. Tests should pass with minimal
  modifications beyond package renames.
- Consider whether `OdysseyEventLog` needs a `delete(String streamKey)` method for
  `stream.delete()`. If so, add it to the SPI in this spec.
- The old `odyssey-redis` module will be replaced by the two new backend modules in
  subsequent specs.
