# json-pojo-gen

A Gradle plugin that generates Java POJOs from JSON. Built with Java 25 and Gradle 9.8.2 (Kotlin DSL).

## Requirements

- JDK 25 on `JAVA_HOME`
- Always use the wrapper: `./gradlew` (or `gradlew.bat` on Windows). Never a system Gradle.

## Build and test

```sh
./gradlew test                      # unit tests (plugin/src/test)
./gradlew functionalTest            # Gradle TestKit tests (plugin/src/functionalTest)
./gradlew check                     # both
./gradlew test --tests 'io.github.pgatzka.SomeTest'
```

Tests use JUnit Jupiter and AssertJ. Base package: `io.github.pgatzka`. Plugin id: `io.github.pgatzka.json-pojo-gen`.

## Project layout

```
plugin/                     the Gradle plugin subproject
  src/main/java             production code
  src/test/java             unit tests
  src/functionalTest/java   functional tests (Gradle TestKit)
docs/specs/                 feature specs; SPEC_TEMPLATE.md is the template
.claude/                    Claude Code orchestrator and agent configuration
```

## Development workflow

This repository is developed in a strict spec-first TDD loop driven by Claude Code.
See [CLAUDE.md](CLAUDE.md) for the orchestrator rules and the agent roles
(`test-writer`, `implementer`, `reviewer`).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
