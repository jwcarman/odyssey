# Implement RedisOdysseyStream

## What to build

Create `RedisOdysseyStream` in `odyssey-redis` — the single implementation of
`OdysseyStream` that backs all three stream types (ephemeral, channel, broadcast).

**Publishing (`publish`):**
1. `XADD` the event to the Redis Stream key (with `MAXLEN ~` trimming)
2. `PUBLISH` the Redis Stream entry ID to `odyssey:notify:<streamKey>` (Pub/Sub wake-up)
3. Return the entry ID

**Subscribing (`subscribe`, `resumeAfter`, `replayLast`):**
1. Create an `SseEmitter` with the appropriate timeout (per-call or default)
2. Create a `SubscriberOutbox` with:
   - A reader function that performs non-blocking `XREAD` from the outbox's last read ID
   - The `SseEmitter`
   - The `keepAliveInterval`
3. Register the outbox with this stream's `TopicFanout`
4. Register an `SseEmitter` completion callback to unregister the outbox and clean up
5. Start the outbox (launches reader + writer threads)
6. Return the `SseEmitter`

For `resumeAfter(lastEventId)`: before starting the outbox, perform `XRANGE` from
`lastEventId` to `+`, deliver those events to the outbox's queue, set the outbox's
`lastReadId` to the highest replayed ID.

For `replayLast(count)`: before starting the outbox, perform
`XREVRANGE <key> + - COUNT min(count, maxLastN)`, reverse the results, deliver to queue,
set `lastReadId`.

**Close/Delete:**
- `close()`: shutdown the `TopicFanout` (graceful — interrupt readers, poison queues)
- `delete()`: shutdown the `TopicFanout` (immediate — interrupt all threads), then
  `DEL` the Redis key

**Configuration received at construction:**
- Stream key (full Redis key)
- Redis commands (shared Lettuce connection — Connection 2)
- `TopicFanout` instance
- `keepAliveInterval`
- Default SSE timeout
- `maxLen` for XADD trimming
- `maxLastN` cap for replayLast
- Stream prefix for the notify channel

## Acceptance criteria

- [ ] `RedisOdysseyStream` implements `OdysseyStream`
- [ ] `publish` performs XADD + PUBLISH and returns the entry ID
- [ ] `subscribe` creates outbox, registers with fanout, returns working SseEmitter
- [ ] `resumeAfter` replays via XRANGE before going live
- [ ] `replayLast` replays via XREVRANGE (reversed) before going live, capped at maxLastN
- [ ] `close` gracefully shuts down all subscribers
- [ ] `delete` immediately shuts down subscribers and DELs the Redis key
- [ ] SseEmitter completion/error/timeout callbacks clean up the outbox
- [ ] `getStreamKey` returns the full Redis key
- [ ] Unit tests with stubbed Redis commands cover publish, subscribe, resume, replay
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- The `TopicFanout` is created lazily or at construction time — one per stream key.
- XADD fields: `eventType`, `payload`, `timestamp`, plus any metadata entries.
- Use `MAXLEN ~ <maxLen>` (approximate trimming) on XADD for performance.
