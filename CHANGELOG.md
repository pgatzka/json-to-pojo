# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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
- Moved the scaffold plugin from package `org.example` to `io.github.pgatzka`; plugin id is now
  `io.github.pgatzka.json-pojo-gen` (was `org.example.greeting`). The `greeting` task is unchanged apart from the id it prints.
  Spec: `docs/specs/package-migration.md`.

### Removed

- The scaffold `greeting` task and the `JsonPojoGenPlugin.GREETING_TASK_NAME` constant. Running `greeting` now fails with
  "Task 'greeting' not found". Spec: `docs/specs/remove-greeting-task.md`.
