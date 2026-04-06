# Scaffold Maven multi-module project

## What to build

Set up the Maven multi-module project structure with all four modules, Maven wrapper,
Spotless formatting, and a compiling skeleton. No implementation code — just the build
infrastructure.

**Parent POM (`odyssey-parent`):**
- `groupId: org.jwcarman.odyssey`, `version: 1.0.0-SNAPSHOT`
- Java 25 compiler settings
- Spring Boot 4.x BOM via `dependencyManagement` (import scope)
- Spotless plugin with Google Java Format
- Maven wrapper (`mvnw`)
- Modules: `odyssey-core`, `odyssey-redis`, `odyssey-autoconfigure`, `odyssey-spring-boot-starter`

**`odyssey-core`:**
- No Spring dependency
- Empty `src/main/java/io/odyssey/core/` directory with a placeholder (package-info.java)
- Empty `src/test/java/io/odyssey/core/`

**`odyssey-redis`:**
- Depends on `odyssey-core`
- Dependencies: `spring-data-redis`, `lettuce-core`
- Empty source directories with package-info.java

**`odyssey-autoconfigure`:**
- Depends on `odyssey-redis`
- Dependencies: `spring-boot-autoconfigure`
- Empty source directories with package-info.java

**`odyssey-spring-boot-starter`:**
- Depends on `odyssey-autoconfigure`
- No source code — dependency-only module
- Follows standard Spring Boot starter conventions

## Acceptance criteria

- [ ] `./mvnw clean verify -DskipTests` succeeds with `BUILD SUCCESS`
- [ ] `./mvnw spotless:check` passes
- [ ] All four modules are listed in the parent POM
- [ ] Each module's POM has the correct dependencies on sibling modules
- [ ] Java 25 is configured as the compiler source/target
- [ ] Spring Boot 4.x BOM is imported in dependency management
- [ ] Maven wrapper files exist and are executable (`mvnw`, `mvnw.cmd`)
- [ ] All existing tests still pass (trivially — no tests yet)

## Implementation notes

- Use `spring-boot-starter-parent` as the parent POM's parent, or import the BOM — either
  approach is fine, but be consistent with Spring Boot 4.x conventions.
- The starter module should have no `src/main/java` — it's just a POM with dependencies.
- Do not add `spring-boot-maven-plugin` to library modules — this is a library, not an app.
