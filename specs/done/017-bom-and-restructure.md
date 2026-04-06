# Add BOM and restructure modules

## What to build

Restructure the Maven project to match the new module layout. Remove the old modules
(`odyssey-redis`, `odyssey-autoconfigure`, `odyssey-spring-boot-starter`) and add the new
ones.

**Changes to parent POM:**
Remove from modules:
- `odyssey-redis`
- `odyssey-autoconfigure`
- `odyssey-spring-boot-starter`

Add to modules:
- `odyssey-bom`
- `odyssey-eventlog-redis`
- `odyssey-notifier-redis`

Keep:
- `odyssey-core`
- `odyssey-example`

**`odyssey-bom` module:**
- `<packaging>pom</packaging>`
- `<dependencyManagement>` listing all published modules with `${project.version}`:
  - `odyssey-core`
  - `odyssey-eventlog-redis`
  - `odyssey-notifier-redis`
- No source code

**Delete old module directories:**
- `odyssey-redis/`
- `odyssey-autoconfigure/`
- `odyssey-spring-boot-starter/`

**Update `odyssey-example`:**
- Remove dependency on `odyssey-spring-boot-starter`
- Add dependencies on `odyssey-core`, `odyssey-eventlog-redis`, `odyssey-notifier-redis`
- Import `odyssey-bom` in its dependency management

## Acceptance criteria

- [ ] `odyssey-bom` module exists with all published modules listed
- [ ] Old modules (`odyssey-redis`, `odyssey-autoconfigure`, `odyssey-spring-boot-starter`)
      are deleted
- [ ] Parent POM lists the correct set of modules
- [ ] `odyssey-example` depends on the new modules and compiles
- [ ] `./mvnw clean verify` passes across all modules
- [ ] `./mvnw spotless:check` passes

## Implementation notes

- The BOM should be built first in the reactor order (list it first in the parent POM
  modules).
- This spec depends on 011-016 being complete — all engine code must be in `odyssey-core`
  and both Redis modules must exist before the old modules can be deleted.
