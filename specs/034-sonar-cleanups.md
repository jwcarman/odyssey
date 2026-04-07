# SonarCloud cleanups

## What to build

Fix remaining SonarCloud issues across the codebase.

### 1. Unnamed patterns for unused exception variables

Java 22+ supports unnamed patterns. Replace `catch (SomeException e)` with
`catch (SomeException _)` when the exception variable is not referenced in the
catch block.

**Before:**
```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

**After:**
```java
} catch (InterruptedException _) {
    Thread.currentThread().interrupt();
}
```

Scan all source files (main and test) for `catch` blocks where the exception variable
is unused.

### 2. Any other SonarCloud issues

Check the SonarCloud report at https://sonarcloud.io/summary/overall?id=jwcarman_odyssey
for remaining issues and fix them. Common patterns to look for:

- Unused imports
- Unused variables or parameters
- Missing `final` on fields that could be final
- Raw type usage
- Empty catch blocks without comments
- String literals that should be constants

## Acceptance criteria

- [ ] No unused exception variables — all use unnamed patterns (`_`)
- [ ] All other SonarCloud issues addressed
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- Unnamed patterns (`_`) require Java 22+. We're on Java 25, so this is available.
- Only replace with `_` when the exception variable is truly unused. If the variable
  is logged or rethrown, keep it named.
