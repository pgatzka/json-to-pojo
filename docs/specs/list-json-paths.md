# Feature: `listJsonPaths` task logs the JSON paths of an input file

## Context

First real feature of the plugin. A build configures a JSON file through the `jsonPojoGen` extension; the new
`listJsonPaths` task reads that file and logs one JSON path per node it finds. The path list is the structural view a
later POJO generator will consume, so arrays are collapsed with the `[*]` wildcard and every node (objects, arrays and
scalars) is listed, not only leaves. Related: `docs/specs/package-migration.md` (existing plugin class and `greeting` task).

Decisions made here (user chose the first two; the rest are the orchestrator's calls):

- Array elements are collapsed to `[*]`; paths below different elements of the same array are merged into one set.
- Every node below the root is listed: objects, arrays, scalars (`null` included). The bare root `$` itself is **not** listed.
- JSON parsing uses Jackson databind (`com.fasterxml.jackson.core:jackson-databind:2.22.4`), added as an `implementation`
  dependency through the version catalog. Jackson preserves member order, so output follows document order.
- Extension name `jsonPojoGen`, single property `inputFile`. Task name `listJsonPaths`, group `json-pojo-gen`.
- The task logs at `lifecycle` level, one path per line, nothing else. It declares no outputs, so Gradle runs it every time.

## Package and class names

| Kind | Fully qualified name | Source set | Notes |
|------|----------------------|------------|-------|
| class | `io.github.pgatzka.JsonPathLister` | `plugin/src/main/java` | new; static utility, no instances |
| class | `io.github.pgatzka.JsonPojoGenExtension` | `plugin/src/main/java` | new; abstract, Gradle-managed |
| class | `io.github.pgatzka.ListJsonPathsTask` | `plugin/src/main/java` | new; abstract `DefaultTask`, Gradle-managed |
| class | `io.github.pgatzka.JsonPojoGenPlugin` | `plugin/src/main/java` | modified: creates the extension and registers the task; `greeting` unchanged |
| test  | `io.github.pgatzka.JsonPathListerTest` | `plugin/src/test/java` | new |
| test  | `io.github.pgatzka.ListJsonPathsTaskTest` | `plugin/src/test/java` | new; `ProjectBuilder` |
| test  | `io.github.pgatzka.JsonPojoGenPluginTest` | `plugin/src/test/java` | modified: add extension/task registration tests, keep existing ones |
| test  | `io.github.pgatzka.JsonPojoGenPluginFunctionalTest` | `plugin/src/functionalTest/java` | modified: add `listJsonPaths` TestKit tests, keep existing ones |

## Public API (exact Java signatures)

```java
package io.github.pgatzka;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Computes the JSON paths of every node in a JSON document. Not instantiable. */
public final class JsonPathLister {

    // private constructor; no public constructor

    /**
     * Lists the JSON paths of all nodes below the root of {@code json}, in pre-order and document order,
     * with array elements collapsed to {@code [*]} and duplicates removed (first occurrence wins).
     * Returns an unmodifiable list; never null; empty if the root is a scalar or an empty container.
     * @throws NullPointerException if {@code json} is null
     * @throws IllegalArgumentException if {@code json} is blank (message contains "empty") or is not valid JSON
     *         (message starts with "Invalid JSON: "; the Jackson exception is the cause)
     */
    public static List<String> listPaths(String json);

    /**
     * Reads {@code file} as UTF-8 and delegates to {@link #listPaths(String)}.
     * @throws NullPointerException if {@code file} is null
     * @throws IOException if the file cannot be read (e.g. does not exist)
     * @throws IllegalArgumentException as for {@link #listPaths(String)}
     */
    public static List<String> listPaths(Path file) throws IOException;
}
```

```java
package io.github.pgatzka;

import org.gradle.api.file.RegularFileProperty;

/** Configuration for the json-pojo-gen plugin, registered as the {@code jsonPojoGen} extension. */
public abstract class JsonPojoGenExtension {

    /** Name under which the extension is registered. Value: "jsonPojoGen". Never null. */
    public static final String NAME = "jsonPojoGen";

    /** Implicit public no-arg constructor (Gradle instantiates the extension). */

    /** The JSON file to process. No default value; never returns null (the property object itself). */
    public abstract RegularFileProperty getInputFile();
}
```

```java
package io.github.pgatzka;

import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Logs the JSON paths found in the configured input file, one per line, at lifecycle level. */
public abstract class ListJsonPathsTask extends DefaultTask {

    /** Name under which the plugin registers this task. Value: "listJsonPaths". Never null. */
    public static final String NAME = "listJsonPaths";

    /** Task group. Value: "json-pojo-gen". Never null. */
    public static final String GROUP = "json-pojo-gen";

    /** Implicit public no-arg constructor (Gradle instantiates the task). */

    /** The JSON file to read. Defaults (via convention) to the extension's {@code inputFile}. Never returns null. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputFile();

    /**
     * Reads {@code inputFile}, computes its JSON paths with {@link JsonPathLister#listPaths(java.nio.file.Path)}
     * and logs each path as its own lifecycle message.
     * @throws IOException if the file cannot be read
     * @throws org.gradle.api.GradleException if the file is not valid JSON; message starts with
     *         "Invalid JSON in " followed by the file's absolute path; the IllegalArgumentException is the cause
     */
    @TaskAction
    public void listPaths() throws IOException;
}
```

`JsonPojoGenPlugin` keeps its existing public API (`PLUGIN_ID`, `GREETING_TASK_NAME`, no-arg constructor, `apply`).
`apply(Project)` now additionally:

1. creates the extension: `project.getExtensions().create(JsonPojoGenExtension.NAME, JsonPojoGenExtension.class)`;
2. registers the task: `project.getTasks().register(ListJsonPathsTask.NAME, ListJsonPathsTask.class, task -> { ... })`,
   setting group `ListJsonPathsTask.GROUP`, description `"Logs the JSON paths found in the configured input file."`, and
   `task.getInputFile().convention(extension.getInputFile())`.

Build script contract (implementer):

`gradle/libs.versions.toml`:

```toml
[versions]
jackson = "2.22.4"

[libraries]
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }
```

`plugin/build.gradle.kts`, inside the existing `dependencies {}` block:

```kotlin
implementation(libs.jackson.databind)
```

## JSON path syntax

- Root is `$`; it is never itself listed.
- Object member `name` appends `.name` when `name` matches `[A-Za-z_][A-Za-z0-9_]*`; otherwise it appends `['name']`
  with every `\` escaped as `\\` and every `'` escaped as `\'` inside the quotes. The empty key appends `['']`.
- An array appends `[*]` once for all its elements. The paths of all elements are merged: a path appears once, at the
  position of its first occurrence.
- Pre-order: a container's own path precedes the paths of its children. Members in document order.

Reference document used by criteria 1 and 10:

```json
{
  "id": 1,
  "name": "widget",
  "address": {"city": "Berlin", "zip": "10115"},
  "tags": ["a", "b"],
  "items": [{"sku": "s1", "qty": 2}, {"sku": "s2", "price": 9.5}],
  "empty": {},
  "none": null
}
```

Expected paths, in exactly this order:

```
$.id
$.name
$.address
$.address.city
$.address.zip
$.tags
$.tags[*]
$.items
$.items[*]
$.items[*].sku
$.items[*].qty
$.items[*].price
$.empty
$.none
```

## Behavior / acceptance criteria

1. `JsonPathLister.listPaths(String)` on the reference document returns exactly the 14 paths above, in that order.
2. `listPaths("{\"a\":{\"b\":{\"c\":true}}}")` returns `["$.a", "$.a.b", "$.a.b.c"]`.
3. Root array: `listPaths("[{\"a\":1},{\"b\":2}]")` returns `["$[*]", "$[*].a", "$[*].b"]`.
4. Nested arrays: `listPaths("[[1,2],[3]]")` returns `["$[*]", "$[*][*]"]`.
5. Non-identifier keys: `listPaths("{\"first name\":1,\"it's\":2,\"a\\\\b\":3,\"\":4,\"_ok9\":5}")` returns
   `["$['first name']", "$['it\\'s']", "$['a\\\\b']", "$['']", "$._ok9"]`
   (as Java strings: `$['first name']`, `$['it\'s']`, `$['a\\b']`, `$['']`, `$._ok9`).
6. Scalar root: `listPaths("42")`, `listPaths("\"x\"")`, `listPaths("null")`, `listPaths("{}")` and `listPaths("[]")`
   all return an empty list.
7. The returned list is unmodifiable: `add("x")` throws `UnsupportedOperationException`.
8. `JsonPathLister.listPaths(Path)` on a UTF-8 file containing `{"ä":1,"b":[true]}` returns `["$['ä']", "$.b", "$.b[*]"]`
   (the key `ä` is not an ASCII identifier, so it uses bracket notation).
9. Applying the plugin (by id or by class) to a `ProjectBuilder` project registers an extension named `jsonPojoGen` whose
   type is `JsonPojoGenExtension` (`project.getExtensions().getByName("jsonPojoGen")` is an instance of it) and a task
   named `listJsonPaths` of type `ListJsonPathsTask` with group `json-pojo-gen` and description
   `Logs the JSON paths found in the configured input file.`. The `greeting` task is still registered.
10. Functional (Gradle TestKit, `withPluginClasspath()`): a project with build script
    ```kotlin
    plugins {
        id("io.github.pgatzka.json-pojo-gen")
    }
    jsonPojoGen {
        inputFile.set(file("input.json"))
    }
    ```
    and `input.json` holding the reference document, running `listJsonPaths`, finishes with outcome `SUCCESS` for
    `:listJsonPaths`, and the build output contains the 14 expected paths, each as a whole line (after `strip()`), in the
    listed relative order. No other output line starts with `$`.
11. Convention wiring (unit, `ProjectBuilder`): after applying the plugin and setting
    `extension.getInputFile().set(someFile)`, `task.getInputFile().get().getAsFile()` equals `someFile`.
    Setting `task.getInputFile().set(otherFile)` afterwards makes the task use `otherFile`.
12. Unit (`ProjectBuilder`, `@TempDir`): with `inputFile` set to a file containing `{"a":[1]}`, calling the task's
    `listPaths()` method directly completes without exception.

## Edge cases and error handling

- `JsonPathLister.listPaths((String) null)` and `listPaths((Path) null)` throw `NullPointerException` (message not asserted).
- `listPaths("")` and `listPaths("   \n")` throw `IllegalArgumentException` whose message contains `empty`.
- `listPaths("{\"a\":")` and `listPaths("not json")` throw `IllegalArgumentException` whose message starts with
  `Invalid JSON: ` and whose cause is a `com.fasterxml.jackson.core.JsonProcessingException`.
- `listPaths(Path)` on a non-existent file throws `IOException` (any subclass, e.g. `NoSuchFileException`).
- Duplicate paths from merged array elements appear once (covered by criteria 1 and 3). Duplicate keys inside one object
  are not specified (Jackson keeps the last value; the path is listed once either way).
- Task, invalid JSON (unit, `ProjectBuilder`, `@TempDir`): `inputFile` set to a file containing `{"a":`; calling `listPaths()`
  throws `org.gradle.api.GradleException` whose message starts with `Invalid JSON in ` and contains the file's absolute
  path, and whose cause is an `IllegalArgumentException`.
- Task, invalid JSON (functional): same build script as criterion 10 with `input.json` holding `{"a":`; `buildAndFail()`
  finishes with outcome `FAILED` for `:listJsonPaths` and the output contains `Invalid JSON in `.
- Task, no `inputFile` configured (functional): build script applies the plugin but has no `jsonPojoGen {}` block;
  `buildAndFail()` output contains `property 'inputFile'` (Gradle's own validation message; exact wording beyond that is not asserted).
- Task, scalar-root document (e.g. `42`): the task succeeds and logs no path lines.
- Non-UTF-8 input files are not supported and not specified.

## Out of scope

- Removing or changing the `greeting` task (`GREETING_TASK_NAME` and its output stay as they are).
- Generating any Java source, class names, or type inference.
- Concrete array indices (`[0]`) or any JSONPath feature beyond `.name`, `['name']` and `[*]`.
- Multiple input files, directories, or wiring `listJsonPaths` into `check`/`build`.
- Task outputs, caching, incremental behavior.
- A `jackson-core`/`jackson-annotations` version override; databind brings them transitively.
