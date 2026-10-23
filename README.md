# json-pojo-gen

A Gradle plugin that generates Java POJOs from JSON. Built with Java 25 and Gradle 9.8.2 (Kotlin DSL).

## Requirements

- A JDK 17 or newer on `JAVA_HOME` to launch Gradle. The build declares a Java 25 toolchain; if no JDK 25 is installed,
  Gradle downloads one automatically through the
  [foojay toolchain resolver](https://github.com/gradle/foojay-toolchains) (needs network access to `api.foojay.io`
  and the vendor's download host, e.g. `github.com` for Temurin).
- Always use the wrapper: `./gradlew` (or `gradlew.bat` on Windows). Never a system Gradle.

## Usage

Apply the plugin and point it at a JSON file:

```kotlin
plugins {
    id("io.github.pgatzka.json-pojo-gen")
}

jsonPojoGen {
    inputFile.set(file("input.json"))
}
```

`./gradlew listJsonPaths` then logs one JSON path per node in the document, in document order. Array elements are
collapsed to `[*]` and merged, so for

```json
{"id": 1, "address": {"city": "Berlin"}, "items": [{"sku": "s1"}, {"sku": "s2", "qty": 2}]}
```

the task prints

```
$.id
$.address
$.address.city
$.items
$.items[*]
$.items[*].sku
$.items[*].qty
```

Keys that are not plain identifiers use bracket notation, e.g. `$['first name']`. The build fails with
`Invalid JSON in <file>` when the file does not parse.

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
