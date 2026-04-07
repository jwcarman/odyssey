# Update BOM with all published modules

## What to build

Ensure `odyssey-bom` includes every published module. The BOM must be updated
whenever a new module is added. The example module is NOT included.

Add any missing modules to `odyssey-bom/pom.xml` under `<dependencyManagement>`.
Each entry should use `${project.version}`.

**All published modules (verify all are present):**
- `odyssey-core`
- `odyssey-eventlog-redis`
- `odyssey-eventlog-postgresql`
- `odyssey-eventlog-cassandra`
- `odyssey-eventlog-dynamodb`
- `odyssey-eventlog-mongodb`
- `odyssey-eventlog-rabbitmq`
- `odyssey-eventlog-nats`
- `odyssey-eventlog-hazelcast`
- `odyssey-notifier-redis`
- `odyssey-notifier-postgresql`
- `odyssey-notifier-nats`
- `odyssey-notifier-sns`
- `odyssey-notifier-rabbitmq`
- `odyssey-notifier-hazelcast`

**NOT included:**
- `odyssey-example`
- `odyssey-bom` (it IS the BOM)

Also ensure the parent POM `<modules>` section includes the new Hazelcast modules.

## Acceptance criteria

- [ ] All published modules listed in `odyssey-bom/pom.xml`
- [ ] No example module in the BOM
- [ ] Parent POM modules includes Hazelcast modules
- [ ] `./mvnw clean verify` passes
- [ ] `./mvnw spotless:check` passes
