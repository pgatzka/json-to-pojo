# Feature: Build tooling — formatting check, coverage, Sonar analysis, publishing

## Context

CI (`docs/specs/github-actions-ci.md`, next spec) needs Gradle tasks it can call: a formatting check, a coverage report for
Sonar, a Sonar analysis task, and publishing to GitHub Packages. This spec adds those to the build. Apart from one
class-level annotation (see Public API) it changes no Java production code; the acceptance criteria are mostly Gradle
invocations checked by the implementer and reviewer, plus one unit test for the annotation.

Decisions made here (user chose the formatter, the Sonar target and the publish cadence):

- Formatter: Spotless 8.10.4 with Palantir Java Format 2.102.0 (4-space indent, 120 columns), plus `removeUnusedImports`,
  `trimTrailingWhitespace`, `endWithNewline`. Java sources only. `spotlessCheck` is part of `check` (Spotless default).
- Coverage: Gradle's `jacoco` plugin, tool version 0.8.15, XML report produced after `test` (Sonar reads the XML).
- Sonar: `org.sonarqube` 7.5.0.8588 applied to the **root** project; SonarCloud, organization `pgatzka`,
  project key `pgatzka_json-to-pojo`. The token is supplied by CI through `SONAR_TOKEN`; nothing token-related in the build.
- Publishing: `maven-publish` to GitHub Packages (`https://maven.pkg.github.com/pgatzka/json-to-pojo`), credentials from the
  `GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables. Coordinates `io.github.pgatzka:json-pojo-gen:0.1.0-SNAPSHOT`
  plus the plugin marker; sources jar included; no javadoc jar.
- Plugin versions go through the version catalog (`[plugins]` section); the formatter and JaCoCo versions through `[versions]`.

## Package and class names

No Java types added or changed. Files changed by the implementer:

| File | Change |
|------|--------|
| `gradle.properties` | add `group` and `version` |
| `gradle/libs.versions.toml` | add versions and `[plugins]` section |
| `build.gradle.kts` (root, **new**) | apply and configure the Sonar plugin |
| `plugin/build.gradle.kts` | apply `maven-publish`, `jacoco`, Spotless; configure them and publishing |
| `plugin/src/**/*.java` | reformatted once by `./gradlew spotlessApply` (see Verification notes) |

## Public API (exact Java signatures)

One annotation is added; nothing else changes. Applying `maven-publish` next to `java-gradle-plugin` turns on Gradle's
stricter plugin validation (`validatePlugins`), which requires every task type to declare its caching behavior.
`ListJsonPathsTask` only logs and declares no outputs, so caching is disabled:

```java
package io.github.pgatzka;

import org.gradle.work.DisableCachingByDefault;

/** Unchanged otherwise (see docs/specs/map-paths.md and docs/specs/typed-json-paths.md). */
@DisableCachingByDefault(because = "Only logs paths and declares no outputs; nothing to cache")
public abstract class ListJsonPathsTask extends DefaultTask { /* unchanged */ }
```

| Kind | Fully qualified name | Source set | Notes |
|------|----------------------|------------|-------|
| class | `io.github.pgatzka.ListJsonPathsTask` | `plugin/src/main/java` | modified: class-level annotation only |
| test  | `io.github.pgatzka.ListJsonPathsTaskTest` | `plugin/src/test/java` | modified: criterion 9 added; existing tests kept |

## Build script contract

`gradle.properties` (append; keep the existing line):

```properties
group=io.github.pgatzka
version=0.1.0-SNAPSHOT
```

`gradle/libs.versions.toml` (add to `[versions]`, add a `[plugins]` table; keep everything that is there):

```toml
[versions]
palantir-java-format = "2.102.0"
jacoco = "0.8.15"
spotless = "8.10.4"
sonarqube = "7.5.0.8588"

[plugins]
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
sonarqube = { id = "org.sonarqube", version.ref = "sonarqube" }
```

`build.gradle.kts` at the repository root (new file, exactly this content):

```kotlin
plugins {
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property("sonar.projectKey", "pgatzka_json-to-pojo")
        property("sonar.organization", "pgatzka")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}
```

`plugin/build.gradle.kts`:

1. `plugins {}` block becomes:
   ```kotlin
   plugins {
       `java-gradle-plugin`
       `maven-publish`
       jacoco
       alias(libs.plugins.spotless)
   }
   ```
2. Inside the existing `java {}` block, after the `toolchain {}` block, add `withSourcesJar()`.
3. Add, after the `java {}` block:
   ```kotlin
   base {
       archivesName = "json-pojo-gen"
   }

   jacoco {
       toolVersion = libs.versions.jacoco.get()
   }

   spotless {
       java {
           target("src/**/*.java")
           palantirJavaFormat(libs.versions.palantir.java.format.get())
           removeUnusedImports()
           trimTrailingWhitespace()
           endWithNewline()
       }
   }

   publishing {
       publications.withType<MavenPublication>().configureEach {
           if (name == "pluginMaven") {
               artifactId = "json-pojo-gen"
           }
           pom {
               name = "json-pojo-gen"
               description = "Gradle plugin that generates Java POJOs from JSON."
               url = "https://github.com/pgatzka/json-to-pojo"
           }
       }
       repositories {
           maven {
               name = "GitHubPackages"
               url = uri("https://maven.pkg.github.com/pgatzka/json-to-pojo")
               credentials {
                   username = providers.environmentVariable("GITHUB_ACTOR").orNull
                   password = providers.environmentVariable("GITHUB_TOKEN").orNull
               }
           }
       }
   }
   ```
4. Add, after the existing `tasks.named<Test>("test") { ... }` block:
   ```kotlin
   tasks.jacocoTestReport {
       dependsOn(tasks.test)
       reports {
           xml.required = true
           html.required = true
       }
   }

   tasks.named<Test>("test") {
       finalizedBy(tasks.jacocoTestReport)
   }
   ```
   (Merging the `finalizedBy` into the existing `tasks.named<Test>("test")` block is equally acceptable.)

Everything else in the file (toolchain, dependencies, `gradlePlugin {}`, functional test source set and task, `check`
wiring) stays as it is. The implementer may fix a compile error in this contract (e.g. an accessor name) but must keep the
behavior; it must not add plugins, dependencies or properties the contract does not list.

## Behavior / acceptance criteria

Criteria 1–8 are checked by running Gradle; criterion 9 has a JUnit test.

1. `./gradlew spotlessCheck` succeeds after the one-time `./gradlew spotlessApply` (criterion 7), and
   `./gradlew check --dry-run` lists `:plugin:spotlessCheck`.
2. `./gradlew check` succeeds and afterwards `plugin/build/reports/jacoco/test/jacocoTestReport.xml` exists.
3. `./gradlew tasks --all` lists the tasks `sonar` (root), `publish`, `publishToMavenLocal`,
   `publishPluginMavenPublicationToGitHubPackagesRepository` and
   `publishJsonPojoGenPluginMarkerMavenPublicationToGitHubPackagesRepository` (all under `:plugin`).
4. `./gradlew publishToMavenLocal` succeeds and `~/.m2/repository/io/github/pgatzka/json-pojo-gen/0.1.0-SNAPSHOT/` contains
   `json-pojo-gen-0.1.0-SNAPSHOT.jar`, `json-pojo-gen-0.1.0-SNAPSHOT-sources.jar` and the `.pom`; and
   `~/.m2/repository/io/github/pgatzka/json-pojo-gen/io.github.pgatzka.json-pojo-gen.gradle.plugin/0.1.0-SNAPSHOT/`
   contains the marker `.pom` whose dependency points at `io.github.pgatzka:json-pojo-gen:0.1.0-SNAPSHOT`.
5. The built jar is named `json-pojo-gen-0.1.0-SNAPSHOT.jar` (`plugin/build/libs/`).
6. `./gradlew sonar --dry-run` lists `:sonar` and configures without error (no network, no token needed for a dry run).
7. `./gradlew spotlessApply` has been run once; it reformats files under `plugin/src/main`, `plugin/src/test` and
   `plugin/src/functionalTest` as needed. After that, `./gradlew check` is green: all existing unit and functional tests pass
   unchanged in behavior.
8. The configuration cache stays enabled (`gradle.properties` keeps `org.gradle.configuration-cache=true`) and
   `./gradlew check` does not report configuration cache problems.
9. Unit test: `ListJsonPathsTask.class` carries `org.gradle.work.DisableCachingByDefault` and its `because()` is not blank.
   `./gradlew validatePlugins` succeeds.

## Edge cases and error handling

- Without `GITHUB_ACTOR` / `GITHUB_TOKEN` set, `publishToMavenLocal`, `check` and `build` must still work; only `publish`
  (to GitHub Packages) fails, with Gradle's own credentials error. Not asserted.
- Without `SONAR_TOKEN`, `./gradlew sonar` (not dry-run) fails against SonarCloud. Not asserted; CI runs it only when the token exists.

## Verification notes

- `spotlessApply` modifies test sources under `plugin/src/test` and `plugin/src/functionalTest`. The implementer never
  reads those files; running the formatter through Gradle is permitted for this spec because it reveals nothing about their
  content. The implementer must not open the reformatted files or the Gradle diff output for them.
- Commands the implementer should use, in order: `./gradlew spotlessApply`, `./gradlew check`, `./gradlew publishToMavenLocal`,
  `./gradlew tasks --all | grep -E 'sonar|publish'`, `./gradlew sonar --dry-run`, `./gradlew check --dry-run | grep spotless`.
- The Gradle daemon here runs on JDK 21 while compilation uses the JDK 25 toolchain; Spotless and JaCoCo run in the daemon JVM.

## Out of scope

- The GitHub Actions workflow itself (next spec).
- Formatting Kotlin build scripts or Markdown; Checkstyle, PMD, Error Prone.
- Publishing to the Gradle Plugin Portal or Maven Central; signing; a javadoc jar; a LICENSE file.
- Release versioning (tags, `-SNAPSHOT` removal), changelog automation.
- Coverage thresholds (`jacocoTestCoverageVerification`).
