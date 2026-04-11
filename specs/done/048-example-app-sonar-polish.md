# Example app Sonar polish

## What to build

Fix all SonarCloud issues in `odyssey-example`. These are cosmetic but contribute to the 24 code-smell count.

### Issues in scope

| Rule | Severity | File | Action |
|---|---|---|---|
| `java:S1192` | CRITICAL | `TaskController.java:51` | The string literal `"progress"` appears 3 times. Extract as a `private static final String PROGRESS_EVENT_TYPE = "progress";` (or similar name based on actual usage). |
| `java:S1481` | MINOR | `BroadcastController.java:43` | Unused local `key`. Delete it. |
| `java:S1481` | MINOR | `NotifyController.java:45` | Unused local `key`. Delete it. |

### Detailed fixes

#### `TaskController.java:51` — S1192

Fetch the file, find the three uses of `"progress"`, extract a constant at the top of the class. Name depends on context; likely `PROGRESS_EVENT_TYPE` if it's an SSE event name or `PROGRESS_FIELD` if it's a map key.

#### Unused `key` locals

Two different controllers have an unused `key` local that was probably left over from refactoring `stream.getStreamKey()` or similar. Just delete them.

### Out of scope

- Main-code issues (spec 046)
- Test-file issues (spec 047)
- Coverage gaps (spec 049)

## Acceptance criteria

- [ ] `TaskController` has a named constant for the `"progress"` literal, used in all three places
- [ ] `BroadcastController.java:43` has no unused `key` local
- [ ] `NotifyController.java:45` has no unused `key` local
- [ ] `./mvnw clean install` passes
- [ ] SonarCloud shows zero open issues for S1192 in `TaskController.java` and S1481 in the example controllers

## Implementation notes

- `odyssey-example` has no tests (`coverage.exclusions = odyssey-example/**` in the parent POM), so there's no test file to update — just the main sources.
- These are trivial but worth doing for quality-gate cleanliness.
