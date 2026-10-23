# Feature: Paths report nullability and optionality

## Context

`TypedJsonPath` (`docs/specs/typed-json-paths.md`) carries a merged type per collapsed path. A POJO generator also needs
to know whether a value may be `null` and whether a property may be missing. This spec adds two boolean flags to
`TypedJsonPath` and appends them to the task output.

Decisions made here:

- `nullable`: at least one node at the collapsed path is JSON `null`. A path whose merged type is `null` is therefore
  always nullable.
- `optional`: only member paths (`.name` / `['name']`) can be optional. Let P be the collapsed path of the parent object
  (`$` for root members). The member is optional when the number of **object** nodes at P is greater than the number of
  nodes at the member path, i.e. at least one parent object lacks the member. `null` or non-object parents are not counted:
  their own `nullable` flag or type carries that information. `[*]` and `.*` paths are never optional (an empty array or
  map is not a missing property).
- Output format: `<path>: <type>` followed by nothing, ` (nullable)`, ` (optional)` or ` (nullable, optional)`.
- `TypedJsonPath` keeps a two-argument convenience constructor with both flags `false`, so the record stays easy to build
  in tests and later code.
- Which paths are listed, their order and their merged type are unchanged.

## Package and class names

| Kind | Fully qualified name | Source set | Notes |
|------|----------------------|------------|-------|
| record | `io.github.pgatzka.TypedJsonPath` | `plugin/src/main/java` | modified: two components and a convenience constructor added |
| class | `io.github.pgatzka.JsonPathLister` | `plugin/src/main/java` | modified: computes the flags; signatures unchanged |
| test | `io.github.pgatzka.TypedJsonPathTest` | `plugin/src/test/java` | modified: flag and format tests added |
| test | `io.github.pgatzka.JsonPathListerTest` | `plugin/src/test/java` | modified: flag tests added; existing expectations adjusted where a flag is now true |
| test | `io.github.pgatzka.JsonPojoGenPluginFunctionalTest` | `plugin/src/functionalTest/java` | modified: expected output lines carry the suffixes |

`ListJsonPathsTask`, `JsonType`, `JsonPojoGenExtension` and `JsonPojoGenPlugin` are unchanged (the task already logs
`toString()`).

## Public API (exact Java signatures)

```java
package io.github.pgatzka;

/**
 * A collapsed JSON path with its merged type and nullability/optionality.
 * @param path     the collapsed path, e.g. "$.items[*].sku"; never null
 * @param type     the merged type; never null
 * @param nullable true if any node at this path is JSON null
 * @param optional true if at least one merged parent object lacks this member
 */
public record TypedJsonPath(String path, JsonType type, boolean nullable, boolean optional) {

    /** Compact constructor: throws NullPointerException if {@code path} or {@code type} is null. */

    /** Equivalent to {@code new TypedJsonPath(path, type, false, false)}. */
    public TypedJsonPath(String path, JsonType type);

    /**
     * Returns {@code path + ": " + type.label()} followed by "" when neither flag is set,
     * " (nullable)", " (optional)", or " (nullable, optional)".
     */
    @Override
    public String toString();
}
```

`JsonPathLister` keeps its four `listPaths` signatures (all returning `List<TypedJsonPath>`); every returned entry now has
the flags computed per the rules above.

## Flag rules

Terms: "nodes at a collapsed path" are all JSON nodes whose collapsed path is that string (several, when under `[*]` or `.*`).

| Flag | Rule |
|---|---|
| `nullable` | true iff at least one node at the path is `null` |
| `optional` | member paths only: true iff `count(object nodes at parent path) > count(nodes at the path)`; `[*]` and `.*` paths: always false |

For root members the parent path is `$` and the root object counts as one object node, so root members are never optional.

Reference document for criteria 1 and 11:

```json
{
  "id": 1,
  "name": "widget",
  "price": 9.5,
  "active": true,
  "address": {"city": "Berlin", "zip": "10115"},
  "tags": ["a", "b"],
  "items": [
    {"sku": "s1", "qty": 2, "note": null, "flag": 1},
    {"sku": "s2", "qty": 2.5, "note": "x", "flag": true, "price": 1.5}
  ],
  "empty": {},
  "none": null
}
```

Expected output (`toString()` of each entry), in exactly this order:

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
$.items[*].note: string (nullable)
$.items[*].flag: string
$.items[*].price: float (optional)
$.empty: object
$.none: null (nullable)
```

## Behavior / acceptance criteria

1. `listPaths(reference)` returns 18 entries whose `toString()` values are exactly the lines above, in that order.
2. Root members: `listPaths("{\"a\":null,\"b\":1}")` yields `$.a` with type `NULL`, `nullable == true`, `optional == false`
   and `$.b` with type `INTEGER` and both flags `false`.
3. Nullable through merging: `listPaths("[{\"a\":1},{\"a\":null}]")` yields `$[*].a: integer (nullable)`;
   `listPaths("[{\"a\":null},{\"a\":null}]")` yields `$[*].a: null (nullable)`; `listPaths("[1,null,\"a\"]")` yields
   `$[*]: string (nullable)`.
4. Optional through merging: `listPaths("[{\"a\":1},{}]")` yields `$[*].a: integer (optional)`;
   `listPaths("[{\"a\":1},{\"b\":2}]")` yields `$[*].a: integer (optional)` and `$[*].b: integer (optional)`.
5. Both flags: `listPaths("[{\"a\":null},{}]")` yields `$[*].a: null (nullable, optional)`;
   `listPaths("[{\"a\":1},{\"a\":null},{}]")` yields `$[*].a: integer (nullable, optional)`.
6. Map values: `listPaths("{\"m\":{\"x\":{\"k\":1},\"y\":{}}}", Set.of("$.m"))` yields `$.m: object`, `$.m.*: object`,
   `$.m.*.k: integer (optional)`; `listPaths("{\"m\":{\"x\":1,\"y\":null}}", Set.of("$.m"))` yields `$.m: object`,
   `$.m.*: integer (nullable)`.
7. Null parents are not counted: `listPaths("[{\"attrs\":{\"a\":1}},{\"attrs\":null}]")` yields
   `$[*].attrs: object (nullable)` and `$[*].attrs.a: integer` (both flags false).
8. `[*]` is never optional: `listPaths("[[1],[]]")` yields `$[*]: array` and `$[*][*]: integer` with both flags false;
   `listPaths("[[1],[null]]")` yields `$[*][*]: integer (nullable)`.
9. Nested chain: `listPaths("[{\"a\":{\"b\":1}},{\"a\":{}}]")` yields `$[*].a: object` and `$[*].a.b: integer (optional)`;
   `listPaths("[{\"a\":{\"b\":1}},{}]")` yields `$[*].a: object (optional)` and `$[*].a.b: integer` (not optional: every
   `a` object has `b`).
10. Record API: `new TypedJsonPath("$.x", JsonType.INTEGER)` equals `new TypedJsonPath("$.x", JsonType.INTEGER, false, false)`
    and prints `$.x: integer`; `new TypedJsonPath("$.x", JsonType.STRING, true, false).toString()` is `$.x: string (nullable)`;
    `(…, false, true)` prints `$.x: string (optional)`; `(…, true, true)` prints `$.x: string (nullable, optional)`.
11. Functional (Gradle TestKit): with the reference document as `input.json`, `listJsonPaths` succeeds and the output
    contains the 18 lines above, each as a whole line (after `strip()`), in that relative order; no other line starts with `$`.
12. Earlier references still hold for `path()` and `type()`: the typed reference of `docs/specs/typed-json-paths.md`
    criterion 1 and the map-path reference of `docs/specs/map-paths.md` criterion 1 (existing tests adapted, not deleted;
    where an entry is now flagged, e.g. `$.none: null (nullable)` or `$.items[*].note: string (nullable)`, the test
    expectation is updated to the flagged value).

## Edge cases and error handling

- Type conflicts and map-path errors are unchanged and are detected regardless of the flags
  (`listPaths("[{\"a\":1},null,[1]]")` still fails with `Conflicting types at $[*]: object, array`).
- Scalar roots and empty containers still yield an empty list.
- `new TypedJsonPath(null, JsonType.NULL, true, false)` and `new TypedJsonPath("$.a", null, false, false)` throw `NullPointerException`.
- Equality includes the flags: `new TypedJsonPath("$.x", INTEGER, true, false)` is not equal to `new TypedJsonPath("$.x", INTEGER)`.

## Out of scope

- Reporting how often a member is present, or distinguishing "absent" from "present as null" beyond the two flags.
- Treating an empty array or empty map as optional.
- Any POJO generation or Java type mapping (e.g. `Optional<T>` or `@Nullable`).
- Changing the task's error messages or the extension.
