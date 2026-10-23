# Feature: `generateJsonPojos` task and extension wiring

## Context

`PojoGenerator` (`docs/specs/pojo-generator.md`) turns a typed path list into Java sources. This spec exposes it in the
build: new extension properties, a cacheable `generateJsonPojos` task, and automatic wiring of the output directory into
the `main` Java source set when the `java` plugin is applied, so `compileJava` compiles the generated classes.

Decisions made here:

- `packageName` is required (no default); Gradle's own validation fails the build when it is missing.
- `rootClassName` defaults to the input file's base name in UpperCamelCase (`product.json` → `Product`,
  `my-order.json` → `MyOrder`), computed with the same key-to-class-name rules as the generator.
- Output directory defaults to `build/generated/sources/jsonPojoGen/java/main`. The task owns it: stale files are
  deleted before each generation.
- The task is `@CacheableTask`; all inputs are declared.
- The plugin does **not** add `jackson-annotations` to the consumer's dependencies; the README tells users to add it
  when their keys need `@JsonProperty`.
- Lister errors keep the exact messages `ListJsonPathsTask` uses; generator errors are wrapped as
  `Code generation failed for <file>: <message>`.

## Package and class names

| Kind | Fully qualified name | Source set | Notes |
|------|----------------------|------------|-------|
| class | `io.github.pgatzka.JsonPojoGenExtension` | `plugin/src/main/java` | modified: six properties added |
| class | `io.github.pgatzka.GenerateJsonPojosTask` | `plugin/src/main/java` | new |
| class | `io.github.pgatzka.JsonPojoGenPlugin` | `plugin/src/main/java` | modified: registers the task, wires conventions and the source set |
| test | `io.github.pgatzka.GenerateJsonPojosTaskTest` | `plugin/src/test/java` | new; `ProjectBuilder`, `@TempDir` |
| test | `io.github.pgatzka.JsonPojoGenPluginTest` | `plugin/src/test/java` | modified: registration and convention tests added |
| test | `io.github.pgatzka.JsonPojoGenPluginFunctionalTest` | `plugin/src/functionalTest/java` | modified: codegen TestKit tests added |

`ListJsonPathsTask`, `JsonPathLister`, `TypedJsonPath`, `JsonType` and the `codegen` package are unchanged.

## Public API (exact Java signatures)

```java
package io.github.pgatzka;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

public abstract class JsonPojoGenExtension {

    public static final String NAME = "jsonPojoGen";                       // unchanged
    public abstract RegularFileProperty getInputFile();                    // unchanged
    public abstract SetProperty<String> getMapPaths();                     // unchanged

    /** Java package of the generated classes. Required; no default. Never returns null. */
    public abstract Property<String> getPackageName();

    /** Class name of the root object. Convention: input file base name in UpperCamelCase. Never returns null. */
    public abstract Property<String> getRootClassName();

    /** Object path → class name overrides. Empty by default. Never returns null. */
    public abstract MapProperty<String, String> getClassNames();

    /** Emit equals/hashCode. Convention: true. Never returns null. */
    public abstract Property<Boolean> getGenerateEqualsAndHashCode();

    /** Emit toString. Convention: true. Never returns null. */
    public abstract Property<Boolean> getGenerateToString();

    /** Where generated sources go. Convention: build/generated/sources/jsonPojoGen/java/main. Never returns null. */
    public abstract DirectoryProperty getOutputDirectory();
}
```

```java
package io.github.pgatzka;

import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Generates Java POJOs from the configured JSON file into the output directory. */
@CacheableTask
public abstract class GenerateJsonPojosTask extends DefaultTask {

    /** Value: "generateJsonPojos". */
    public static final String NAME = "generateJsonPojos";

    /** Value: "json-pojo-gen". */
    public static final String GROUP = "json-pojo-gen";

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputFile();

    @Input
    public abstract SetProperty<String> getMapPaths();

    @Input
    public abstract Property<String> getPackageName();

    @Input
    public abstract Property<String> getRootClassName();

    @Input
    public abstract MapProperty<String, String> getClassNames();

    @Input
    public abstract Property<Boolean> getGenerateEqualsAndHashCode();

    @Input
    public abstract Property<Boolean> getGenerateToString();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Deletes the contents of the output directory, lists the paths of the input file with the configured map paths,
     * generates the classes with PojoGenerator and writes each file (UTF-8) at its relativePath() below the output
     * directory. Logs one lifecycle line: "Generated <n> Java file(s) into <absolute output dir>".
     * @throws IOException if reading or writing fails
     * @throws org.gradle.api.GradleException with the same messages as ListJsonPathsTask for lister failures
     *         ("Invalid JSON in <file>: ...", "Map path error for <file>: ...", "Type conflict in <file>: ..."), and
     *         "Code generation failed for <file>: <message>" for an IllegalArgumentException from GeneratorOptions or
     *         PojoGenerator; the original exception is the cause. <file> is the input file's absolute path.
     */
    @TaskAction
    public void generate() throws IOException;
}
```

`JsonPojoGenPlugin.apply(Project)` additionally:

1. Sets conventions on the extension: `generateEqualsAndHashCode` and `generateToString` to `true`;
   `outputDirectory` to `project.getLayout().getBuildDirectory().dir("generated/sources/jsonPojoGen/java/main")`;
   `rootClassName` to a provider derived from `inputFile`: the file name without its last extension, converted to
   UpperCamelCase with the generator's rules (split on non-identifier characters, capitalize each part; `product.json` →
   `Product`, `my-order.json` → `MyOrder`, `2fa.json` → `_2fa`).
2. Registers `generateJsonPojos` of type `GenerateJsonPojosTask` with group `GROUP`, description
   `"Generates Java POJOs from the configured JSON file."`, and every task property set by convention from the
   extension property of the same name (`inputFile`, `mapPaths`, `packageName`, `rootClassName`, `classNames`,
   `generateEqualsAndHashCode`, `generateToString`, `outputDirectory`).
3. When the `java` plugin is applied (`project.getPluginManager().withPlugin("java", ...)`, so order does not matter),
   adds the task's output directory to the `main` source set via
   `sourceSets.getByName("main").getJava().srcDir(taskProvider.flatMap(GenerateJsonPojosTask::getOutputDirectory))`,
   which makes `compileJava` depend on `generateJsonPojos`.

## Behavior / acceptance criteria

Reference input and expected classes: the `product.json` document and the four classes of `docs/specs/pojo-generator.md`
("Generation rules" → "Reference input"), with `mapPaths` `$.translations` and `$.items[*].attrs`, package `com.acme.model`.

1. Unit (`ProjectBuilder`): applying the plugin registers a task `generateJsonPojos` of type `GenerateJsonPojosTask`,
   group `json-pojo-gen`, description `Generates Java POJOs from the configured JSON file.`; `listJsonPaths` is still registered.
2. Unit: extension conventions: `getGenerateEqualsAndHashCode().get()` and `getGenerateToString().get()` are `true`,
   `getClassNames().get()` is empty, `getOutputDirectory().get().getAsFile()` equals
   `<project build dir>/generated/sources/jsonPojoGen/java/main`, `getPackageName().isPresent()` is `false`.
3. Unit: after `extension.getInputFile().set(file("product.json"))`, `extension.getRootClassName().get()` is `Product`;
   for `my-order.json` it is `MyOrder`; an explicit `extension.getRootClassName().set("Thing")` wins.
4. Unit: every task property follows the extension (`packageName`, `rootClassName`, `classNames`, both flags,
   `outputDirectory`, plus `inputFile` and `mapPaths`), and an explicit value set on the task overrides the extension.
5. Unit (`@TempDir`): with the reference input, `packageName` `com.acme.model`, map paths set and `outputDirectory` set to
   a temp dir, calling `generate()` directly writes exactly `com/acme/model/Product.java`, `Address.java`, `Item.java`
   and `Translation.java` below the output directory (UTF-8, each starting with `package com.acme.model;`) and nothing else.
6. Unit: stale files are removed: a pre-existing `com/acme/model/Old.java` (and an unrelated `junk.txt`) in the output
   directory no longer exist after `generate()`.
7. Unit: with `generateToString` set to `false` on the task, the written `Address.java` contains no `toString`.
8. Unit, errors (`@TempDir`): input `{"a":` → `GradleException` starting with `Invalid JSON in ` + absolute path;
   input `{"billing":{"address":{"a":1}},"shipping":{"address":{"b":2}}}` → `GradleException` whose message starts with
   `Code generation failed for ` + absolute path and contains `Class name Address used by both`, cause
   `IllegalArgumentException`; `packageName` set to `com.9x` → `GradleException` starting with `Code generation failed for `
   and containing `Invalid package name`.
9. Functional (TestKit): a project with
   ```kotlin
   plugins {
       java
       id("io.github.pgatzka.json-pojo-gen")
   }
   repositories { mavenCentral() }
   dependencies { implementation("com.fasterxml.jackson.core:jackson-annotations:2.22") }
   ```
   (Since Jackson 2.20 the annotations module is versioned without a patch number: `2.22`, not `2.22.4`.)
   ```kotlin
   jsonPojoGen {
       inputFile.set(file("product.json"))
       mapPaths.add("\$.translations")
       mapPaths.add("\$.items[*].attrs")
       packageName.set("com.acme.model")
   }
   ```
   and the reference `product.json`: running `compileJava` finishes with `SUCCESS` for both `:generateJsonPojos` and
   `:compileJava`; the four `.java` files exist under `build/generated/sources/jsonPojoGen/java/main/com/acme/model/`;
   `build/classes/java/main/com/acme/model/Product.class`, `Address.class`, `Item.class`, `Translation.class` exist.
10. Functional: running `compileJava` a second time without changes gives `UP-TO-DATE` for `:generateJsonPojos`.
11. Functional: after removing the `address` member from `product.json`, running `compileJava` again gives `SUCCESS` for
    `:generateJsonPojos`, `Address.java` no longer exists in the output directory, and `Address.class` no longer exists
    under `build/classes/java/main/com/acme/model/`.
12. Functional: without the `java` plugin (only `id("io.github.pgatzka.json-pojo-gen")`) and the same extension block,
    running `generateJsonPojos` finishes with `SUCCESS` and writes the four files.
13. Functional: with the `java` plugin but no `packageName`, running `generateJsonPojos` fails (`buildAndFail()`) and the
    output contains `property 'packageName'`.
14. Functional: with `generateEqualsAndHashCode.set(false)` and `generateToString.set(false)` in the extension block,
    the generated `Address.java` contains neither `equals(` nor `hashCode(` nor `toString(`.

## Edge cases and error handling

- `rootClassName` convention for a file named `.json` or `-.json` (empty base name after conversion): the provider
  yields `_` so Gradle validation does not fail at configuration time; at execution `GeneratorOptions` rejects `_`
  (a keyword since Java 9) and the task fails with `Code generation failed for <file>: Invalid class name ...`, which
  tells the user to set `rootClassName`. Not asserted beyond "does not throw at configuration time".
- The output directory is created if missing. Only its contents are deleted, never the directory's parent.
- Applying the `java` plugin **after** `jsonPojoGen` still wires the source set (`withPlugin` reacts to later application).
  Unit test: apply `io.github.pgatzka.json-pojo-gen` first, then `java`, and assert that
  `sourceSets.main.java.srcDirs` contains the task's output directory.
- Configuration cache: the task uses only managed properties; `./gradlew check` must not report configuration cache
  problems (the functional test projects run with the TestKit default).

## Out of scope

- Adding `jackson-annotations` (or any dependency) to the consumer's configurations.
- Multiple input files or directories; per-file options.
- Wiring into Kotlin or other source sets; IDE integration metadata.
- Generating into `src/` or committing generated sources.
