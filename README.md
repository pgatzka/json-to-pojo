# json-pojo-gen

[![CI](https://github.com/pgatzka/json-to-pojo/actions/workflows/ci.yml/badge.svg)](https://github.com/pgatzka/json-to-pojo/actions/workflows/ci.yml)

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

`./gradlew listJsonPaths` then logs one line per node in the document, in document order, as `<path>: <type>`,
optionally followed by `(nullable)`, `(optional)` or `(nullable, optional)` (see Types below).
Array elements are collapsed to `[*]` and merged, so for

```json
{"id": 1, "address": {"city": "Berlin"}, "items": [{"sku": "s1", "qty": 2}, {"sku": "s2", "qty": 2.5}]}
```

the task prints

```
$.id: integer
$.address: object
$.address.city: string
$.items: array
$.items[*]: object
$.items[*].sku: string
$.items[*].qty: float
```

Keys that are not plain identifiers use bracket notation, e.g. `$['first name']`. The build fails with
`Invalid JSON in <file>` when the file does not parse.

### Types

Each path carries one of `float`, `integer`, `string`, `boolean`, `object`, `array` or `null`. Where values are merged
(array elements, map values), the types are merged too:

- `null` always loses to any other type.
- `integer` and `float` together give `float`.
- any other mix of scalars (`string`, `integer`, `float`, `boolean`) gives `string`.
- `object` or `array` mixed with any other non-null type fails the build with
  `Type conflict in <file>: Conflicting types at <path>: <types>`.

Two flags may follow the type. `(nullable)` means a `null` was seen at that path. `(optional)` means at least one of the
merged parent objects did not have the member at all. Both can appear: `$.items[*].note: string (nullable, optional)`.
A parent that is itself `null` is reported as nullable and does not make its children optional.

### Objects that are maps

Some objects are keyed by data rather than by field names, such as `"translations": {"en": {...}, "de": {...}}`.
List their paths in `mapPaths` and the task collapses their members to `.*`, merging the values the same way it
merges array elements:

```kotlin
jsonPojoGen {
    inputFile.set(file("input.json"))
    mapPaths.add("\$.translations")
    mapPaths.add("\$.items[*].attrs")
}
```

For `{"translations": {"en": {"text": "Hello"}, "de": {"text": "Hallo"}}}` this prints `$.translations: object`,
`$.translations.*: object` and `$.translations.*.text: string` instead of one entry per language. A map path must match a path the
task would otherwise print, and it must point to an object (or `null`); otherwise the build fails with
`Map path error for <file>`.

## Generating POJOs

With the `java` plugin applied, `generateJsonPojos` writes one mutable POJO per JSON object into
`build/generated/sources/jsonPojoGen/java/main` and adds that directory to the `main` source set, so `compileJava`
compiles them. Only `packageName` is required:

```kotlin
plugins {
    java
    id("io.github.pgatzka.json-pojo-gen")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.22") // only needed for @JsonProperty
}

jsonPojoGen {
    inputFile.set(file("product.json"))
    mapPaths.add("\$.translations")
    packageName.set("com.acme.model")
    rootClassName.set("Product")               // default: the file name in UpperCamelCase (product.json -> Product)
    classNames.put("\$.items[*]", "LineItem")  // override a derived class name
    generateEqualsAndHashCode.set(true)        // default true
    generateToString.set(true)                 // default true
}
```

For `{"id": 1, "items": [{"sku": "s1"}, {"sku": "s2", "qty": 2.5}], "translations": {"en": {"text": "Hi"}}}` this
produces `Product`, `LineItem` and `Translation`:

```java
public class Product {
    private long id;                              // integer, always present -> primitive
    private List<LineItem> items;                 // [*] -> List
    private Map<String, Translation> translations; // map path -> Map
    // no-arg constructor, getters, setters, equals/hashCode, toString
}

public class LineItem {
    private String sku;
    private Double qty;                           // missing in one element -> optional -> boxed
}
```

Mapping rules:

- `integer`, `float`, `boolean` become `long`, `double`, `boolean` when the member is always present and never null,
  otherwise `Long`, `Double`, `Boolean`. `string` is `String`, a `null`-only path is `Object`.
- Class names come from the key, singularized under `[*]` and `.*` (`items` → `Item`, `addresses` → `Address`).
  Two objects that would get the same name fail the build; disambiguate with `classNames`.
- A key that is not a Java identifier becomes a camelCase field; whenever Jackson's getter-derived name differs from
  the key, the getter and setter carry `@JsonProperty("<key>")`.
- Reserved names (`Object`, `String`, `List`, `Map`, …) and empty keys fail the build with a message naming the path.

## Build and test

```sh
./gradlew test                      # unit tests (plugin/src/test)
./gradlew functionalTest            # Gradle TestKit tests (plugin/src/functionalTest)
./gradlew check                     # both, plus spotlessCheck and the JaCoCo report
./gradlew test --tests 'io.github.pgatzka.SomeTest'
./gradlew spotlessApply             # format Java sources (Palantir Java Format)
./gradlew publishToMavenLocal       # install io.github.pgatzka:json-pojo-gen into ~/.m2
```

Tests use JUnit Jupiter and AssertJ. Base package: `io.github.pgatzka`. Plugin id: `io.github.pgatzka.json-pojo-gen`.

Formatting is enforced by Spotless with Palantir Java Format; `check` fails on unformatted code, `spotlessApply` fixes
it. Coverage is written to `plugin/build/reports/jacoco/test/jacocoTestReport.xml` after `test`. `./gradlew sonar`
sends an analysis to SonarCloud (organization `pgatzka`, project `pgatzka_json-to-pojo`) and needs `SONAR_TOKEN` in
the environment. `./gradlew publish` pushes the plugin and its marker to GitHub Packages and needs `GITHUB_ACTOR` and
`GITHUB_TOKEN`.

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main` and on every pull request:

1. `./gradlew check` on Temurin 25: unit and functional tests, Spotless formatting check, JaCoCo report.
2. `./gradlew sonar`, only when the `SONAR_TOKEN` repository secret is set (create the project
   `pgatzka_json-to-pojo` in SonarCloud organization `pgatzka` and add the token as a secret).
3. On pushes to `main` only: `./gradlew publish` to GitHub Packages, using the workflow's own `GITHUB_TOKEN`.

Test and coverage reports are uploaded as a workflow artifact when the build fails.

Dependabot (`.github/dependabot.yml`) opens one grouped pull request per week for the workflow actions and one for the
Gradle plugins and libraries. It does not update the Gradle wrapper; bump that by hand with
`./gradlew wrapper --gradle-version <version>`.

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
