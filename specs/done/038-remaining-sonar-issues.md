# Fix remaining 17 SonarCloud issues

## What to build

Fix all 17 remaining SonarCloud issues to get a clean bill of health.

### 1. S1130 — Unnecessary `throws Exception` on test methods (5 issues)

Remove `throws Exception` from test method signatures where no checked exception
can actually be thrown.

Files:
- `StreamReaderTest.java:73`
- `PostgresOdysseyStreamNotifierTest.java:60, 88, 120, 156`

### 2. S1186 — Empty method bodies (2 issues)

Add `// no-op` comments to empty method bodies to make intent explicit.

Files:
- `OdysseyAutoConfiguration.java:50`
- `InMemoryOdysseyStreamNotifier.java:31`

### 3. S1192 — Duplicate string literal "metadata" (1 issue)

Extract `"metadata"` to a `private static final String` constant in
`DynamoDbOdysseyEventLog.java:94`.

### 4. S1874 — Deprecated `LocalStackContainer` (4 issues)

Update `DynamoDbEventLogAutoConfigurationIT.java` to use the non-deprecated
Testcontainers 2.x `LocalStackContainer`. The class may have moved packages
in Testcontainers 2.x — check the current location and update imports.

### 5. S2589 — Expression always evaluates to false (1 issue)

`NatsOdysseyEventLog.java:247` — remove the dead code or fix the condition.

### 6. S5738 — Deprecated method calls (3 issues)

MongoDB module uses deprecated methods:
- `MongoOdysseyEventLog.java:61, 66` — deprecated API calls. Find the replacement
  methods in the current MongoDB driver version.
- `MongoEventLogAutoConfigurationTest.java:43` — same, update test.

### 7. S6244 — DynamoDB nested builder (1 issue)

`DynamoDbOdysseyEventLog.java:185` — use the Consumer Builder method instead of
creating a nested builder. Example:

**Before:**
```java
DeleteRequest.builder().key(Map.of(...)).build()
```

**After:**
```java
b -> b.key(Map.of(...))
```

## Acceptance criteria

- [ ] All 17 SonarCloud issues fixed
- [ ] Zero open issues on SonarCloud
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- These are all small, mechanical fixes. No architectural changes needed.
- For the deprecated LocalStackContainer, check if Testcontainers 2.x has a
  `LocalStackContainer` in a different package, or if it's been replaced entirely.
- For the MongoDB deprecated methods, check the Spring Data MongoDB / MongoDB
  driver docs for the replacement API.
