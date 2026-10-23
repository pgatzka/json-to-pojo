# Feature: Map paths — treat configured JSON objects as maps

## Context

`listJsonPaths` (`docs/specs/list-json-paths.md`) lists every member of every object. Some objects are not records but
maps keyed by data (`"translations": {"en": {...}, "de": {...}}`): their keys must not become fields. The user configures
such objects by JSON path; the lister then collapses the object's members with the `.*` wildcard and merges the member
values, exactly as it already does for array elements with `[*]`.

Decisions made here:

- Configuration is a `SetProperty<String>` named `mapPaths` on the extension, mirrored on the task with a convention. Empty by default.
- A map path is matched by **string equality** against the collapsed path the lister computes for a node, so it uses the
  lister's own syntax (`$`, `.name`, `['name']`, `[*]`, `.*`). `$.items[*].attrs` marks the `attrs` object of every element;
  `$.i18n.*.meta` marks an object inside another map's values.
- A map path that matches nothing, or that matches an array or a non-null scalar, is an error. A matched `null` is tolerated
  (sample data often has `"attrs": null` next to real objects).
- The wildcard for map values is `.*`, JSONPath's member wildcard.

## Package and class names

| Kind | Fully qualified name | Source set | Notes |
|------|----------------------|------------|-------|
| class | `io.github.pgatzka.JsonPathLister` | `plugin/src/main/java` | modified: two overloads added |
| class | `io.github.pgatzka.JsonPojoGenExtension` | `plugin/src/main/java` | modified: `getMapPaths()` added |
| class | `io.github.pgatzka.ListJsonPathsTask` | `plugin/src/main/java` | modified: `getMapPaths()` added, action passes it on |
| class | `io.github.pgatzka.JsonPojoGenPlugin` | `plugin/src/main/java` | modified: wires the `mapPaths` convention |
| test  | `io.github.pgatzka.JsonPathListerTest` | `plugin/src/test/java` | modified: map-path tests added; existing tests kept |
| test  | `io.github.pgatzka.ListJsonPathsTaskTest` | `plugin/src/test/java` | modified: map-path tests added; existing tests kept |
| test  | `io.github.pgatzka.JsonPojoGenPluginTest` | `plugin/src/test/java` | modified: convention wiring test added |
| test  | `io.github.pgatzka.JsonPojoGenPluginFunctionalTest` | `plugin/src/functionalTest/java` | modified: map-path TestKit tests added |

## Public API (exact Java signatures)

```java
package io.github.pgatzka;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class JsonPathLister {

    /** Unchanged. Equivalent to {@code listPaths(json, Set.of())}. */
    public static List<String> listPaths(String json);

    /** Unchanged. Equivalent to {@code listPaths(file, Set.of())}. */
    public static List<String> listPaths(Path file) throws IOException;

    /**
     * Like {@link #listPaths(String)}, but every object whose collapsed path is contained in {@code mapPaths}
     * is treated as a map: its members are collapsed to {@code <path>.*} and their value paths merged
     * (first occurrence wins), exactly as array elements are collapsed to {@code [*]}.
     * Returns an unmodifiable list; never null.
     * @throws NullPointerException if {@code json} or {@code mapPaths} is null, or {@code mapPaths} contains null
     * @throws IllegalArgumentException if {@code json} is blank or invalid (as before); if a map path is blank or does
     *         not start with {@code $} (message starts with "Invalid map path"); if a map path matches no node
     *         (message starts with "Map path <p> not found"); or if a map path matches an array or a non-null scalar
     *         (message starts with "Map path <p> does not point to a JSON object")
     */
    public static List<String> listPaths(String json, Set<String> mapPaths);

    /**
     * Reads {@code file} as UTF-8 and delegates to {@link #listPaths(String, Set)}.
     * @throws NullPointerException if {@code file} or {@code mapPaths} is null
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException as for {@link #listPaths(String, Set)}
     */
    public static List<String> listPaths(Path file, Set<String> mapPaths) throws IOException;
}
```

```java
package io.github.pgatzka;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;

public abstract class JsonPojoGenExtension {

    public static final String NAME = "jsonPojoGen";                  // unchanged

    public abstract RegularFileProperty getInputFile();               // unchanged

    /** JSON paths of objects to treat as maps. Empty by default (the property has a value); never returns null. */
    public abstract SetProperty<String> getMapPaths();
}
```

```java
package io.github.pgatzka;

import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

public abstract class ListJsonPathsTask extends DefaultTask {

    public static final String NAME = "listJsonPaths";                // unchanged
    public static final String GROUP = "json-pojo-gen";               // unchanged

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputFile();               // unchanged

    /** JSON paths of objects to treat as maps. Defaults (via convention) to the extension's {@code mapPaths}. Never returns null. */
    @Input
    public abstract SetProperty<String> getMapPaths();

    /**
     * Reads {@code inputFile}, computes its JSON paths with {@link JsonPathLister#listPaths(java.nio.file.Path, java.util.Set)}
     * using {@code mapPaths}, and logs each path as its own lifecycle message.
     * @throws IOException if the file cannot be read
     * @throws org.gradle.api.GradleException if the file is not valid JSON (message starts with "Invalid JSON in "
     *         followed by the file's absolute path, unchanged), or if a map path is invalid, not found or does not point
     *         to an object (message starts with "Map path error for " followed by the file's absolute path, then ": "
     *         and the lister's message); in both cases the IllegalArgumentException is the cause
     */
    @TaskAction
    public void listPaths() throws IOException;
}
```

`JsonPojoGenPlugin.apply` additionally sets `task.getMapPaths().convention(extension.getMapPaths())`.

## Map path rules

- Matching: a node is a map node when its collapsed path (the string the lister would otherwise list for it) is an element
  of `mapPaths`. The root object's collapsed path is `$`.
- A map node that is an object with at least one member: list `<path>` (unless it is the root `$`), then `<path>.*`, then
  the merged paths of all member values under `<path>.*` in document order, first occurrence wins. Its member names never appear.
- A map node that is an empty object: list `<path>` only (consistent with empty arrays producing no `[*]`).
- A map node that is `null`: listed as a scalar, no error. It still counts as a match for the not-found rule.
- A map node that is an array, string, number or boolean: `IllegalArgumentException`, message starts with
  `Map path <p> does not point to a JSON object`.
- A map path matched by no node after the whole document has been traversed: `IllegalArgumentException`, message starts
  with `Map path <p> not found`.
- Order of checks: map-path syntax (blank / no leading `$`) is validated before parsing; invalid JSON is reported before any
  not-found or not-an-object error. When several map paths are wrong, any one of them may be reported.

Reference document for criteria 1, 2 and 10:

```json
{
  "id": 7,
  "translations": {"en": {"text": "Hello", "plural": false}, "de": {"text": "Hallo"}},
  "items": [{"sku": "s1", "attrs": {"color": "red"}}, {"sku": "s2", "attrs": {"size": 3}}],
  "empty": {}
}
```

With `mapPaths = {"$.translations", "$.items[*].attrs"}` the expected paths, in exactly this order, are:

```
$.id
$.translations
$.translations.*
$.translations.*.text
$.translations.*.plural
$.items
$.items[*]
$.items[*].sku
$.items[*].attrs
$.items[*].attrs.*
$.empty
```

## Behavior / acceptance criteria

1. `listPaths(reference, Set.of("$.translations", "$.items[*].attrs"))` returns exactly the 11 paths above, in that order.
2. `listPaths(reference, Set.of())` equals `listPaths(reference)`, and that list contains `$.translations.en`,
   `$.translations.de.text` and `$.items[*].attrs.color` (i.e. no collapsing without map paths).
3. Root as map: `listPaths("{\"a\":{\"x\":1},\"b\":{\"y\":2}}", Set.of("$"))` returns `["$.*", "$.*.x", "$.*.y"]`.
4. Map inside a map: `listPaths("{\"i18n\":{\"en\":{\"meta\":{\"k\":1}},\"de\":{\"meta\":{\"j\":2}}}}", Set.of("$.i18n", "$.i18n.*.meta"))`
   returns `["$.i18n", "$.i18n.*", "$.i18n.*.meta", "$.i18n.*.meta.*"]`.
5. Empty object as map: `listPaths("{\"m\":{}}", Set.of("$.m"))` returns `["$.m"]`.
6. Null tolerated: `listPaths("{\"items\":[{\"attrs\":{\"a\":1}},{\"attrs\":null}]}", Set.of("$.items[*].attrs"))`
   returns `["$.items", "$.items[*]", "$.items[*].attrs", "$.items[*].attrs.*"]`.
7. Bracket-notation map path: `listPaths("{\"first name\":{\"a\":1}}", Set.of("$['first name']"))`
   returns `["$['first name']", "$['first name'].*"]`.
8. `listPaths(Path, Set)` on a UTF-8 file containing `{"m":{"a":1}}` with `Set.of("$.m")` returns `["$.m", "$.m.*"]`.
9. Unit (`ProjectBuilder`): after applying the plugin, `extension.getMapPaths().get()` is empty and `task.getMapPaths().get()`
   is empty; after `extension.getMapPaths().add("$.m")`, `task.getMapPaths().get()` contains exactly `$.m`; after
   `task.getMapPaths().set(Set.of("$.n"))`, `task.getMapPaths().get()` contains exactly `$.n`.
10. Functional (Gradle TestKit, `withPluginClasspath()`): build script
    ```kotlin
    plugins {
        id("io.github.pgatzka.json-pojo-gen")
    }
    jsonPojoGen {
        inputFile.set(file("input.json"))
        mapPaths.add("$.translations")
        mapPaths.add("$.items[*].attrs")
    }
    ```
    with `input.json` holding the reference document; running `listJsonPaths` finishes with `SUCCESS` for `:listJsonPaths`
    and the output contains the 11 expected paths, each as a whole line (after `strip()`), in the listed relative order;
    no other output line starts with `$`. (In Kotlin DSL `$` inside a string must be written `\$` or the string put in
    raw triple quotes with `${'$'}`; the test-writer chooses.)
11. Functional, map path to an array: same as criterion 10 but `mapPaths.add("$.items")` only; `buildAndFail()` finishes
    with `FAILED` for `:listJsonPaths` and the output contains `Map path error for ` and `does not point to a JSON object`.
12. Unit (`ProjectBuilder`, `@TempDir`): `inputFile` set to a file containing `{"a":{"b":1}}` and `task.getMapPaths().set(Set.of("$.a"))`;
    calling `listPaths()` directly completes without exception.

## Edge cases and error handling

- `listPaths("{}", null)` and `listPaths(Path, null)` throw `NullPointerException`. A set containing `null`
  (e.g. `new HashSet<>(Arrays.asList((String) null))`) throws `NullPointerException`.
- `Set.of("")`, `Set.of("  ")` and `Set.of("items")` (no leading `$`) each throw `IllegalArgumentException` whose message
  starts with `Invalid map path`.
- `listPaths("{\"a\":1}", Set.of("$.nope"))` throws `IllegalArgumentException` whose message starts with `Map path $.nope not found`.
- `listPaths("{\"a\":[1]}", Set.of("$.a"))`, `listPaths("{\"a\":1}", Set.of("$.a"))` and `listPaths("[1]", Set.of("$"))` each throw
  `IllegalArgumentException` whose message starts with `Map path <p> does not point to a JSON object` (with the respective path).
- Two map paths where one is unmatched: `listPaths("{\"a\":{\"x\":1}}", Set.of("$.a", "$.b"))` throws `IllegalArgumentException`
  whose message starts with `Map path $.b not found`.
- Invalid JSON together with map paths: `listPaths("{\"a\":", Set.of("$.a"))` throws `IllegalArgumentException` whose message
  starts with `Invalid JSON: ` (the JSON error wins).
- Task, unit (`@TempDir`): `inputFile` holding `{"a":1}` and `mapPaths` set to `Set.of("$.nope")`; `listPaths()` throws
  `org.gradle.api.GradleException` whose message starts with `Map path error for `, contains the file's absolute path and
  contains `Map path $.nope not found`; cause is an `IllegalArgumentException`.
- Task, unit: invalid JSON still produces the unchanged `Invalid JSON in ...` `GradleException` (existing tests keep passing).
- Duplicate entries cannot occur (`Set`). Map paths that point to objects whose members are themselves map paths work via
  collapsed-path matching (criterion 4).

## Out of scope

- Wildcards, regular expressions or prefixes in map paths; only exact collapsed paths.
- Inferring maps automatically, key type inference, or any POJO generation.
- Warnings instead of errors for unmatched map paths.
- Changing the existing `.name` / `['name']` / `[*]` syntax or the `Invalid JSON in` message.
