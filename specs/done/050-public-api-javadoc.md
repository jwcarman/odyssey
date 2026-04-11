# Add class/interface-level javadoc to all public API types

## What to build

Every public type in the Odyssey public API should have a class/interface-level javadoc
block explaining what it is, what it's for, and how callers are expected to use it. Today,
9 of the 10 public types in `odyssey-core` lack any type-level javadoc. The exception is
`SseEventMapper`, which got javadoc during the terminal-state redesign.

This is quality polish for the 1.0 release. Javadoc is the difference between an API
someone can pick up from autocomplete and an API they need to read the source to
understand.

### Files to update

- `odyssey-core/src/main/java/org/jwcarman/odyssey/core/Odyssey.java`
- `odyssey-core/src/main/java/org/jwcarman/odyssey/core/OdysseyPublisher.java`
- `odyssey-core/src/main/java/org/jwcarman/odyssey/core/PublisherConfig.java`
- `odyssey-core/src/main/java/org/jwcarman/odyssey/core/SubscriberConfig.java`
- `odyssey-core/src/main/java/org/jwcarman/odyssey/core/DeliveredEvent.java`
- `odyssey-core/src/main/java/org/jwcarman/odyssey/core/PublisherCustomizer.java`
- `odyssey-core/src/main/java/org/jwcarman/odyssey/core/SubscriberCustomizer.java`
- `odyssey-core/src/main/java/org/jwcarman/odyssey/autoconfigure/OdysseyAutoConfiguration.java`
- `odyssey-core/src/main/java/org/jwcarman/odyssey/autoconfigure/OdysseyProperties.java`

### What each doc should cover

- **`Odyssey`** — the top-level facade. Mention producer/consumer split, the three
  sugared stream categories, and the starting-position verbs on the subscriber side.
- **`OdysseyPublisher<T>`** — typed publisher with `AutoCloseable` semantics. Mention
  lifecycle: constructor eagerly provisions via Substrate, `close()` calls `complete()`
  with the configured retention, `delete()` is destructive.
- **`PublisherConfig`** — three TTL knobs. Flag the inactivity-TTL asymmetry (ignored on
  `connect` fallback).
- **`SubscriberConfig<T>`** — 7 knobs. Mention that the starting position is NOT set here
  (it's in the `Odyssey.subscribe/resume/replay` method name).
- **`DeliveredEvent<T>`** — what `SseEventMapper.map` receives. Public record, field
  purposes.
- **`PublisherCustomizer`** / **`SubscriberCustomizer`** — marker interfaces for Spring
  bean customization. Stack order: hardcoded defaults → customizer beans → per-call
  customizer.
- **`OdysseyAutoConfiguration`** — Spring Boot auto-configuration entry point. Mention
  the required `JournalFactory` bean.
- **`OdysseyProperties`** — `@ConfigurationProperties` bound to `odyssey.*`. Document
  the property names and defaults.

### Method-level javadoc

Out of scope for this spec. Most methods have descriptive names and short parameter
lists; class-level documentation is the priority. If method javadoc is wanted, file a
follow-up spec.

## Acceptance criteria

- [ ] Every file listed above has a non-empty class/interface-level javadoc block
      immediately above the `public` declaration
- [ ] Each javadoc at minimum explains what the type represents and what callers should
      do with it; no placeholder text
- [ ] `./mvnw clean install` passes
- [ ] SonarCloud shows no new code smells from the javadoc additions
- [ ] No tests need updating (javadoc-only changes)

## Implementation notes

- Keep each javadoc block under 25 lines. Quality over comprehensiveness.
- Don't document implementation details — users shouldn't care that Journal sits behind
  the scenes. Describe the abstraction, not the mechanism.
- Don't repeat what the method signature already says. Focus on the "why" and the
  non-obvious constraints.
- Cross-reference related types with `{@link ...}` where helpful.
- The `package-info.java` under `org.jwcarman.odyssey.core` already exists — check if it
  has a package-level overview and extend it if useful.
