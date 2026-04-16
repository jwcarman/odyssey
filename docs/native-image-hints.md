# GraalVM native-image hints for odyssey

## Context

Exercised against `cowork-connector-example` (Spring Boot 4.0.5, Java 25) using the GraalVM tracing agent (`-agentlib:native-image-agent`). Odyssey 0.9.0 currently ships **zero** `META-INF/native-image/` metadata and **no** `RuntimeHintsRegistrar` / `BeanRegistrationAotProcessor`. This document describes what needs to ship to make odyssey native-image-ready.

Pattern reference: mocapi 0.4.0-SNAPSHOT (`mocapi-server/src/main/java/com/callibrity/mocapi/server/autoconfigure/aot/MocapiRuntimeHints.java`) and ripcurl 2.6.0-SNAPSHOT (`ripcurl-autoconfigure/src/main/java/com/callibrity/ripcurl/autoconfigure/aot/RipCurlRuntimeHints.java`) both use this pattern.

## Agent-captured surface (7 entries)

- `OdysseyAutoConfiguration`
- `OdysseyProperties`
- `SseProperties`
- `Odyssey` (core interface)
- `TtlPolicy` (core)
- `DefaultOdyssey` (engine)
- `StoredEvent` (engine — package-private record)

## Coverage analysis

| Category | Handled by |
|---|---|
| `OdysseyAutoConfiguration` + `OdysseyProperties` + `SseProperties` | ✅ Spring AOT (record-based `@ConfigurationProperties`) |
| `@PropertySource("classpath:odyssey-defaults.properties")` on `OdysseyAutoConfiguration` | ✅ Spring AOT (`ConfigurationClassPostProcessor` registers the resource hint during AOT processing) |
| `Odyssey` / `DefaultOdyssey` Spring bean | ✅ Spring AOT |
| `TtlPolicy` (record, bound via constructor as a nested `@ConfigurationProperties`) | ✅ Reachable via Spring's configuration-properties AOT |
| **`StoredEvent`** | ⚠️ **Needs explicit hints** |

## What odyssey needs to ship

**One `RuntimeHintsRegistrar`** that registers binding hints on `StoredEvent`. That record is the wire envelope odyssey stores via substrate's journal:

```java
// odyssey/.../engine/DefaultOdysseyStream.java
private final Journal<StoredEvent> journal;

// in emit(...)
StoredEvent event = new StoredEvent(eventType, json, Map.of());
// -> codec.encode(event) via substrate's Journal<StoredEvent>
```

Substrate's `DefaultJournal` hands `StoredEvent` values to a `Codec<StoredEvent>` from the configured `CodecFactory` (typically Jackson). Without binding hints on `StoredEvent`, Jackson can't see the record's components (`eventType`, `data`, `metadata`) in a native image, and every SSE emit / consume fails.

`BindingReflectionHintsRegistrar` walks record components transitively, so registering `StoredEvent` covers the `Map<String, String> metadata` component automatically.

## Implementation sketch

Place under `odyssey/src/main/java/org/jwcarman/odyssey/engine/` — same package as `StoredEvent`, so the registrar can reference the package-private record directly without making it part of odyssey's public API:

```java
package org.jwcarman.odyssey.engine;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class OdysseyRuntimeHints implements RuntimeHintsRegistrar {

  private static final BindingReflectionHintsRegistrar BINDING =
      new BindingReflectionHintsRegistrar();

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    BINDING.registerReflectionHints(hints.reflection(), StoredEvent.class);
  }
}
```

### Why this package and not a dedicated `aot/` sub-package

Three options were considered:

1. **Same package as `StoredEvent`** (`engine`). Registrar can reference the package-private record directly. The runtime package gets one extra class, but `StoredEvent` stays package-private. **Picked.**
2. **Dedicated `engine.aot` sub-package.** Keeps AOT plumbing isolated, but Java sub-packages don't share package-private access — so this forces either promoting `StoredEvent` to `public` (leaks an internal wire type into the API) or a `Class.forName("org.jwcarman.odyssey.engine.StoredEvent")` lookup (brittle string coupling). Not worth the trade for one class.
3. **`autoconfigure/aot`.** Same access problem as option 2, and it's not really autoconfigure concern — `StoredEvent` lives in `engine`.

Register it in `odyssey/src/main/resources/META-INF/spring/aot.factories`:

```
org.springframework.aot.hint.RuntimeHintsRegistrar=\
org.jwcarman.odyssey.engine.OdysseyRuntimeHints
```

## Test pattern

Mirror `mocapi-server/src/test/java/.../aot/MocapiRuntimeHintsTest.java`:

```java
class OdysseyRuntimeHintsTest {

  private final RuntimeHints hints = new RuntimeHints();

  {
    new OdysseyRuntimeHints().registerHints(hints, getClass().getClassLoader());
  }

  @Test
  void registersBindingHintsForStoredEvent() {
    assertThat(hints.reflection().typeHints())
        .anyMatch(th -> th.getType().equals(TypeReference.of(StoredEvent.class)));
  }
}
```

## Notes

- User event payload types are serialized by `DefaultOdysseyStream` to a JSON `String` via the caller's `ObjectMapper` *before* they are wrapped in a `StoredEvent`. That means user payload reflection goes through Spring's Jackson hints — users don't need to register their payload types with odyssey.
- The binding hints are Jackson-shaped. Odyssey doesn't pin a codec — substrate's `CodecFactory` chooses — but in practice every deployment that reaches this code path uses `codec-jackson`, which is a direct dependency of the `odyssey` module. If a non-reflective codec (e.g., a hand-rolled `StoredEvent` codec) is ever introduced, these hints become redundant rather than wrong.
- Verify by running the cowork-connector-example under a native build that exercises an SSE stream. If events flow end-to-end without `MissingReflectionRegistrationError`, odyssey's hints are correct.
