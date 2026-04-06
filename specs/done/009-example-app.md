# Example application with static HTML

## What to build

Create a standalone Spring Boot example application that demonstrates all three stream
types with a simple static HTML page for interactive testing.

**Module:** `odyssey-example` (added to parent POM, but not deployed/published)

**REST endpoints:**

1. `POST /api/broadcast` — publish a message to the "announcements" broadcast stream.
   Accepts `{ "message": "..." }`.
2. `GET /api/broadcast/stream` — subscribe to the broadcast stream. Supports
   `Last-Event-ID` header for resume. Returns `text/event-stream`.
3. `POST /api/notify/{userId}` — publish a notification for a specific user channel.
   Accepts `{ "message": "..." }`.
4. `GET /api/notify/{userId}/stream` — subscribe to a user's channel. Supports
   `Last-Event-ID` header.
5. `POST /api/task` — start a simulated long-running task (ephemeral). Returns the
   stream key in the response body so the client can connect.
6. `GET /api/task/{streamKey}/stream` — subscribe to a task's ephemeral stream. The task
   publishes progress events and a final "complete" event, then closes.

**Static HTML page (`src/main/resources/static/index.html`):**

A single page with three sections, one per stream type:
- **Broadcast:** "Subscribe" button, message input + "Send" button, event log
- **User Channel:** user ID input, "Subscribe" button, message input + "Send" button,
  event log
- **Ephemeral Task:** "Start Task" button, event log showing progress + completion

Use plain HTML/CSS/JavaScript — no frameworks. Use `EventSource` API with
`lastEventId` support for automatic reconnection.

**Application config (`application.yml`):**
```yaml
spring:
  threads:
    virtual:
      enabled: true
  data:
    redis:
      host: localhost
      port: 6379

odyssey:
  keep-alive-interval: 15s
```

## Acceptance criteria

- [ ] `odyssey-example` module exists and compiles
- [ ] All six REST endpoints work correctly
- [ ] Static HTML page loads at `http://localhost:8080/`
- [ ] Broadcast: multiple browser tabs receive the same message
- [ ] User channel: only the subscribed user receives messages
- [ ] Ephemeral task: progress events stream in, final event completes the stream
- [ ] `EventSource` reconnects automatically and resumes via `Last-Event-ID`
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- The example app depends on `odyssey-spring-boot-starter` — that's it. Demonstrates the
  consumer experience.
- Use `spring-boot-maven-plugin` only in this module (it's an app, not a library).
- The simulated task should sleep for a few seconds, publishing 3-4 progress events, then
  a completion event, then `stream.close()`.
- Keep the HTML simple and functional — this is a demo, not a production UI.
- A local Redis instance is required. Add a note in the example's README or a
  docker-compose.yml with Redis.
