# Feature: JSON paths carry a type

## Context

`listJsonPaths` lists collapsed paths (`docs/specs/list-json-paths.md`, `docs/specs/map-paths.md`). A POJO generator
also needs the value type at each path. This spec adds a `JsonType` per path, merges types where nodes are merged
(array elements with `[*]`, map values with `.*`), and changes the task output to `<path>: <type>`.

Decisions made here (the user set the merge rules; the rest are the orchestrator's calls):

- Seven types: `float`, `integer`, `string`, `boolean`, `object`, `array`, `null`.
- Merge rules, applied to the distinct types seen for one collapsed path:
  1. `null` always loses: drop it when any other type is present.
  2. One type left → that type.
  3. `object` or `array` together with any other type → error (a container cannot be coerced).
  4. Only `integer` and `float` left → `float`.
  5. Any other mix of scalars (`string`, `integer`, `float`, `boolean`) → `string`.
- `listPaths` now returns `List<TypedJsonPath>` instead of `List<String>`. Which paths are listed and in which order is
  unchanged; only the element type changes. Existing tests are adapted to read `path()`.
- `TypedJsonPath.toString()` is `<path>: <label>`; the task logs exactly that.

## Package and class names

| Kind | Fully qualified name | Source set | Notes |
|------|----------------------|------------|-------|
| enum | `io.github.pgatzka.JsonType` | `plugin/src/main/java` | new |
| record | `io.github.pgatzka.TypedJsonPath` | `plugin/src/main/java` | new |
| class | `io.github.pgatzka.JsonPathLister` | `plugin/src/main/java` | modified: return type of all four `listPaths` overloads |
| class | `io.github.pgatzka.ListJsonPathsTask` | `plugin/src/main/java` | modified: logs `<path>: <type>`, wraps type conflicts |
| test | `io.github.pgatzka.JsonTypeTest` | `plugin/src/test/java` | new |
| test | `io.github.pgatzka.TypedJsonPathTest` | `plugin/src/test/java` | new |
| test | `io.github.pgatzka.JsonPathListerTest` | `plugin/src/test/java` | modified: existing assertions adapted to `path()`; type tests added |
| test | `io.github.pgatzka.ListJsonPathsTaskTest` | `plugin/src/test/java` | modified: conflict test added |
| test | `io.github.pgatzka.JsonPojoGenPluginFunctionalTest` | `plugin/src/functionalTest/java` | modified: expected output lines now carry types; conflict test added |

`JsonPojoGenExtension` and `JsonPojoGenPlugin` are unchanged.

## Public API (exact Java signatures)

```java
package io.github.pgatzka;

/** The JSON value type observed at a path. */
public enum JsonType {
    FLOAT, INTEGER, STRING, BOOLEAN, OBJECT, ARRAY, NULL;

    /** Lower-case name used in output: "float", "integer", "string", "boolean", "object", "array", "null". Never null. */
    public String label();
}
```

```java
package io.github.pgatzka;

/**
 * A collapsed JSON path together with its (merged) value type.
 * @param path the collapsed path, e.g. "$.items[*].sku"; never null
 * @param type the merged type; never null
 */
public record TypedJsonPath(String path, JsonType type) {

    /** Compact constructor: throws NullPointerException if {@code path} or {@code type} is null. */

    /** Returns {@code path + ": " + type.label()}, e.g. "$.id: integer". */
    @Override
    public String toString();
}
```

```java
package io.github.pgatzka;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class JsonPathLister {

    /** As before, but typed. Equivalent to {@code listPaths(json, Set.of())}. */
    public static List<TypedJsonPath> listPaths(String json);

    /** As before, but typed. Equivalent to {@code listPaths(file, Set.of())}. */
    public static List<TypedJsonPath> listPaths(Path file) throws IOException;

    /**
     * Same paths, same order and same map-path behavior as docs/specs/map-paths.md, each paired with its merged type.
     * Unmodifiable; never null.
     * @throws NullPointerException / IllegalArgumentException as before, plus IllegalArgumentException when merge rule 3
     *         fails (message exactly "Conflicting types at <path>: <labels>", labels in first-seen order, null excluded)
     */
    public static List<TypedJsonPath> listPaths(String json, Set<String> mapPaths);

    /** Reads {@code file} as UTF-8 and delegates to {@link #listPaths(String, Set)}. */
    public static List<TypedJsonPath> listPaths(Path file, Set<String> mapPaths) throws IOException;
}
```

`ListJsonPathsTask` keeps its signatures. `listPaths()` now logs `typedPath.toString()` per entry (`<path>: <label>`), and
throws `org.gradle.api.GradleException` whose message starts with `Type conflict in ` followed by the file's absolute
path, then `: ` and the lister's message, with the `IllegalArgumentException` as cause, when the lister reports
conflicting types. The `Invalid JSON in ` and `Map path error for ` messages are unchanged.

## Type rules

Per node:

| JSON value | `JsonType` |
|---|---|
| `{...}` | `OBJECT` |
| `[...]` | `ARRAY` |
| `"..."` | `STRING` |
| `true` / `false` | `BOOLEAN` |
| `null` | `NULL` |
| number without fraction or exponent (`1`, `-7`, `12345678901234567890`) | `INTEGER` |
| number with fraction or exponent (`1.0`, `-0.5`, `2.5e3`) | `FLOAT` |

(With Jackson: `isIntegralNumber()` → `INTEGER`, `isFloatingPointNumber()` → `FLOAT`.)

Merging: all nodes that share one collapsed path (elements of one array, values of one map object, and everything
beneath them) contribute their type. The distinct types are collected in first-seen document order, then rules 1–5 above
apply. A collapsed path keeps the position of its first occurrence even when a later node decides its type.

Which paths are listed does not change: when `$[*].a` is an object in one element, its children are listed as before,
whatever the merge outcome for `$[*].a` is (if the merge fails, the error is thrown instead).

Reference document for criteria 1 and 9:

```json
{
  "id": 1,
  "name": "widget",
  "price": 9.5,
  "active": true,
  "address": {"city": "Berlin", "zip": "10115"},
  "tags": ["a", "b"],
  "items": [{"sku": "s1", "qty": 2, "note": null, "flag": 1}, {"sku": "s2", "qty": 2.5, "note": "x", "flag": true}],
  "empty": {},
  "none": null
}
```

Expected output, in exactly this order:

```
$.id: integer
$.name: string
$.price: float
$.active: boolean
$.address: object
$.address.city: string
$.address.zip: string
$.tags: array
$.tags[*]: string
$.items: array
$.items[*]: object
$.items[*].sku: string
$.items[*].qty: float
$.items[*].note: string
$.items[*].flag: string
$.empty: object
$.none: null
```

## Behavior / acceptance criteria

1. `listPaths(reference)` returns exactly the 17 `TypedJsonPath` entries above, in that order (compare `path()` and `type()`).
2. Per-node types: `listPaths("{\"a\":1,\"b\":1.0,\"c\":-7,\"d\":2.5e3,\"e\":\"x\",\"f\":false,\"g\":null,\"h\":[],\"i\":{},\"j\":12345678901234567890}")`
   yields types `INTEGER, FLOAT, INTEGER, FLOAT, STRING, BOOLEAN, NULL, ARRAY, OBJECT, INTEGER` for `$.a` … `$.j`.
3. Null loses: `listPaths("[null,1]")` and `listPaths("[1,null]")` both yield `$[*]: INTEGER`; `listPaths("[null,null]")` yields `$[*]: NULL`;
   `listPaths("[null,{\"x\":1}]")` yields `$[*]: OBJECT` and `$[*].x: INTEGER`.
4. Float wins over integer: `listPaths("[1,2.5]")`, `listPaths("[2.5,1]")` and `listPaths("[1,null,2.5]")` all yield `$[*]: FLOAT`.
5. String wins over scalars: `listPaths("[\"a\",1]")`, `listPaths("[1,\"a\"]")`, `listPaths("[1,true]")`, `listPaths("[2.5,false]")`,
   `listPaths("[true,\"a\"]")`, `listPaths("[1,2.5,true]")` and `listPaths("[null,1,false]")` all yield `$[*]: STRING`.
6. Map values merge the same way: `listPaths("{\"m\":{\"a\":1,\"b\":2.5,\"c\":null}}", Set.of("$.m"))` yields
   `$.m: OBJECT`, `$.m.*: FLOAT`; `listPaths("{\"m\":{\"a\":1,\"b\":true}}", Set.of("$.m"))` yields `$.m: OBJECT`, `$.m.*: STRING`.
7. `JsonType.label()` returns `"float"`, `"integer"`, `"string"`, `"boolean"`, `"object"`, `"array"`, `"null"` for the seven
   constants respectively; `TypedJsonPath` with `"$.id"` and `INTEGER` has `toString()` equal to `"$.id: integer"`; two
   `TypedJsonPath` with equal components are equal.
8. Previously specified path lists still hold when mapped to `path()`: the 14-path reference of
   `docs/specs/list-json-paths.md` criterion 1 and the 11-path reference of `docs/specs/map-paths.md` criterion 1
   (existing tests adapted, not deleted).
9. Functional (Gradle TestKit): build script applying the plugin with `inputFile.set(file("input.json"))` and the reference
   document; running `listJsonPaths` finishes with `SUCCESS` and the output contains the 17 lines above, each as a whole
   line (after `strip()`), in that relative order; no other output line starts with `$`.
10. Functional, conflict: `input.json` holding `[{"a":{"x":1}},{"a":[1]}]`; `buildAndFail()` finishes with `FAILED` for
    `:listJsonPaths` and the output contains `Type conflict in ` and `Conflicting types at $[*].a: object, array`.
11. Unit (`ProjectBuilder`, `@TempDir`): `inputFile` holding `[1,"a"]`; calling `listPaths()` directly completes without exception
    (the merge yields string, not an error).

## Edge cases and error handling

- Container conflicts throw `IllegalArgumentException` with the exact message:
  - `listPaths("[{\"a\":1},[1]]")` → `Conflicting types at $[*]: object, array`
  - `listPaths("[{\"a\":1},1]")` → `Conflicting types at $[*]: object, integer`
  - `listPaths("[\"s\",[1]]")` → `Conflicting types at $[*]: string, array`
  - `listPaths("[null,{\"a\":1},true]")` → `Conflicting types at $[*]: object, boolean` (null excluded from the list)
  - `listPaths("{\"items\":[{\"a\":{\"x\":1}},{\"a\":\"s\"}]}")` → `Conflicting types at $.items[*].a: object, string`
- Nested merge: `listPaths("[[1],[2.5]]")` yields `$[*]: ARRAY`, `$[*][*]: FLOAT`.
- Task, unit (`@TempDir`): `inputFile` holding `[{"a":1},[1]]`; `listPaths()` throws `org.gradle.api.GradleException` whose message
  starts with `Type conflict in `, contains the file's absolute path and contains `Conflicting types at $[*]: object, array`;
  cause is an `IllegalArgumentException`.
- Map-path errors (`does not point to a JSON object`, `not found`) and `Invalid JSON` behave exactly as before; when a document
  has both a map-path error and a type conflict, either may be reported.
- The returned list is unmodifiable.
- `new TypedJsonPath(null, JsonType.NULL)` and `new TypedJsonPath("$.a", null)` throw `NullPointerException`.

## Out of scope

- Nullability tracking (whether a path was ever `null`), integer width (`int` vs `long`), or number precision.
- Any POJO generation or Java type mapping.
- Changing which paths are listed, their order, or the map-path semantics.
- A configurable or different output format.
