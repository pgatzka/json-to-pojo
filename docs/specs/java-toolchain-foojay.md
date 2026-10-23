# Feature: Java 25 toolchain with automatic provisioning (foojay resolver)

## Context

The project targets Java 25, but the build does not declare a Java toolchain: Gradle compiles and runs tests with whatever
JDK launches the daemon. On a machine where that JDK is older (for example JDK 21), the build silently uses the wrong
Java level. This spec declares a Java 25 toolchain for the `plugin` subproject and adds the
`org.gradle.toolchains.foojay-resolver-convention` settings plugin so that Gradle downloads a matching JDK from the
foojay Disco API when none is installed locally. No Java production code changes.

Decisions made here (not asked of the user):

- Resolver version: `1.0.0`, the newest version published on the Gradle Plugin Portal at the time of writing.
- The version is written literally in `settings.gradle.kts`, not through the version catalog, to keep the settings script self-contained.
- Toolchain `languageVersion` only; no `vendor` or `implementation` constraint, so any JDK 25 found locally is accepted.

## Package and class names

| Kind | Fully qualified name | Source set | Notes |
|------|----------------------|------------|-------|
| test  | `io.github.pgatzka.JavaToolchainTest` | `plugin/src/test/java` | unit test; new |

No production class is added or changed. The implementer changes build scripts only.

## Public API (exact Java signatures)

None. There is no new or changed Java production code.

Build script contract (both files changed by the implementer; the test-writer may read them but must not change them):

`settings.gradle.kts` (the `plugins {}` block must be the first statement in the file, before `rootProject.name`; keep the
existing `rootProject.name` and `include` lines unchanged):

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "json-pojo-gen"
include("plugin")
```

`plugin/build.gradle.kts` (add this block after the existing `repositories {}` block; nothing else in the file changes):

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

## Behavior / acceptance criteria

1. The JVM that runs the unit tests is Java 25: in `JavaToolchainTest`, `Runtime.version().feature()` equals `25`.
   This holds regardless of which JDK launched Gradle, because the `test` task uses the declared toolchain.
2. `settings.gradle.kts` applies plugin id `org.gradle.toolchains.foojay-resolver-convention` with version `1.0.0`
   in a `plugins {}` block that is the first statement of the file (verified by the reviewer, not by a test).
3. `plugin/build.gradle.kts` declares `java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }`
   (verified by the reviewer, not by a test).
4. On a machine without a local JDK 25, `./gradlew test` provisions one automatically via the foojay resolver instead
   of failing with "No matching toolchains found" (verified by the reviewer by reading the scripts; see Verification notes).
5. All pre-existing tests (`JsonPojoGenPluginTest`, `JsonPojoGenPluginFunctionalTest`) still pass.

## Edge cases and error handling

- None in Java code; this spec adds one assertion-only test and build configuration.
- `JavaToolchainTest` must not read `JAVA_HOME` or `java.home`; it asserts only on `Runtime.version().feature()`.

## Verification notes

The Claude Code cloud sandbox in which this spec is implemented has JDK 21 on `JAVA_HOME` and its network policy
**denies `api.foojay.io`** (and the vendor download hosts behind it). In that sandbox, `./gradlew test` will fail during
toolchain provisioning with a message such as `Unable to download toolchain matching the requirements`,
`Cannot find a Java installation on your machine matching this tasks requirements`, or a connection error naming
`api.foojay.io`. That failure is environmental, not a defect in the implementation.

Implementer: after applying the two build-script changes, run `./gradlew test` once. If the only failure is the toolchain
download described above, do **not** spend further fix attempts; report
`Could not verify with ./gradlew test because api.foojay.io is unreachable from this environment.` and stop.
Do not remove or weaken the toolchain declaration to make the build green.

## Out of scope

- Setting a toolchain vendor, a daemon JVM criteria (`updateDaemonJvm`), or `org.gradle.java.installations.*` properties.
- Changing the Gradle wrapper version, the version catalog, or any dependency.
- Fixing the unrelated deprecation warning in `plugin/build.gradle.kts` (`val functionalTest by tasks.registering`).
- Any JSON-to-POJO functionality or changes to `JsonPojoGenPlugin`.
- CI configuration (the repository has none).
