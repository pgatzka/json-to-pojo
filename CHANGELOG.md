# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `generateJsonPojos` task (cacheable) and extension properties `packageName` (required), `rootClassName` (default:
  input file name in UpperCamelCase), `classNames`, `generateEqualsAndHashCode`, `generateToString` and
  `outputDirectory` (default `build/generated/sources/jsonPojoGen/java/main`). With the `java` plugin applied the
  output directory joins the `main` source set, so `compileJava` compiles the generated classes. Stale files are
  removed before each generation. Spec: `docs/specs/generate-pojos-task.md`.
- `io.github.pgatzka.codegen.PojoGenerator` with `GeneratorOptions` and `JavaFile`: turns a typed path list into
  mutable Java POJOs, one top-level class per JSON object. `integer`/`float`/`boolean` map to `long`/`double`/`boolean`,
  boxed when nullable or optional; arrays to `List<T>`, map paths to `Map<String, T>`, `null`-only paths to `Object`.
  Class names derive from keys (singularized under `[*]`/`.*`) with per-path overrides; collisions, reserved names
  (`java.lang` types, `List`, `Map`, …) and restricted identifiers fail. Keys that are not identifiers become camelCase
  fields; whenever Jackson's getter-derived name differs from the key, getter and setter carry `@JsonProperty`.
  Accessor collisions and a `Class` member are rejected. `equals`/`hashCode` and `toString` are each behind a flag.
  Spec: `docs/specs/pojo-generator.md`.
- `TypedJsonPath` carries `nullable` (a `null` was seen at the path) and `optional` (a merged parent object lacks the
  member) flags; `listJsonPaths` appends `(nullable)`, `(optional)` or `(nullable, optional)` to the line.
  Spec: `docs/specs/nullable-optional-paths.md`.
- Dependabot configuration: weekly grouped updates for GitHub Actions (`ci:` prefix) and Gradle dependencies
  (`build:` prefix). Spec: `docs/specs/dependabot.md`.
- GitHub Actions workflow `CI`: on pushes to `main` and pull requests it runs `./gradlew check` on Temurin 25, runs the
  Sonar analysis when `SONAR_TOKEN` is configured, uploads reports on failure, and on `main` publishes the snapshot to
  GitHub Packages. Spec: `docs/specs/github-actions-ci.md`.
- Build tooling: Spotless (Palantir Java Format) formatting check as part of `check`, JaCoCo XML coverage report,
  `org.sonarqube` plugin targeting SonarCloud (`pgatzka_json-to-pojo`), and `maven-publish` to GitHub Packages as
  `io.github.pgatzka:json-pojo-gen:0.1.0-SNAPSHOT` with a sources jar. `ListJsonPathsTask` is annotated
  `@DisableCachingByDefault`. Spec: `docs/specs/build-tooling.md`.
- Every listed path carries a type (`float`, `integer`, `string`, `boolean`, `object`, `array`, `null`) via the new
  `JsonType` enum and `TypedJsonPath` record. Merged values merge their types: `null` loses, `integer` + `float` gives
  `float`, other scalar mixes give `string`, and a container mixed with another type fails the build with
  `Type conflict in <file>`. `listJsonPaths` now logs `<path>: <type>`; `JsonPathLister.listPaths` returns
  `List<TypedJsonPath>`. Spec: `docs/specs/typed-json-paths.md`.
- `mapPaths` on the `jsonPojoGen` extension and the `listJsonPaths` task: objects at the given JSON paths are treated
  as maps, their members collapsed to `.*` and merged like array elements. Unmatched paths or paths that do not point
  to an object fail the build with `Map path error for <file>`. Spec: `docs/specs/map-paths.md`.
- `jsonPojoGen` extension with an `inputFile` property and a `listJsonPaths` task that logs one JSON path per node of
  the input document (arrays collapsed to `[*]`, non-identifier keys in bracket notation). Jackson databind 2.22.4 is
  now an implementation dependency. Spec: `docs/specs/list-json-paths.md`.
- Spec-first TDD orchestrator setup for Claude Code: `CLAUDE.md`, `test-writer`, `implementer`
  and `reviewer` agents, path-isolation hooks, and `docs/specs/SPEC_TEMPLATE.md`.
- AssertJ 3.27.3 as a test dependency.
- Java 25 toolchain declaration for the `plugin` subproject and the `org.gradle.toolchains.foojay-resolver-convention`
  1.0.0 settings plugin, so Gradle provisions a JDK 25 automatically when none is installed locally.
  Spec: `docs/specs/java-toolchain-foojay.md`.

### Changed

- The `implementer` agent may now edit `settings.gradle.kts`, `plugin/build.gradle.kts` and `gradle/libs.versions.toml`
  whenever a spec spells out the build-script change (plugin, toolchain or dependency), not only for a new dependency.
- The `implementer` agent may also write CI configuration under `.github/**` when a spec spells it out; the
  `agent-guard` hook enforces this.
- Workflow step 3: the orchestrator runs `./gradlew spotlessApply` after the test-writer, so the implementer's `check`
  never fails on test formatting (whose failure output would show test lines).
- Moved the scaffold plugin from package `org.example` to `io.github.pgatzka`; plugin id is now
  `io.github.pgatzka.json-pojo-gen` (was `org.example.greeting`). The `greeting` task is unchanged apart from the id it prints.
  Spec: `docs/specs/package-migration.md`.

### Removed

- The scaffold `greeting` task and the `JsonPojoGenPlugin.GREETING_TASK_NAME` constant. Running `greeting` now fails with
  "Task 'greeting' not found". Spec: `docs/specs/remove-greeting-task.md`.
