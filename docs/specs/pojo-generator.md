# Feature: `PojoGenerator` — Java source files from typed JSON paths

## Context

`JsonPathLister` (`docs/specs/typed-json-paths.md`, `docs/specs/nullable-optional-paths.md`) produces a list of
`TypedJsonPath` entries with merged type, `nullable` and `optional` flags. This spec adds the pure code generator that
turns such a list into Java source files: mutable POJOs, one top-level class per JSON object. It has no Gradle
dependency; the Gradle task that runs it and wires the output into compilation is the next spec
(`docs/specs/generate-pojos-task.md`).

Decisions made with the user:

- Mutable POJOs: private fields, public no-arg constructor, getters and setters; `equals`/`hashCode` and `toString`
  each behind a flag, both on by default.
- Numbers: `integer` → `long`, `float` → `double`. Primitives (`long`, `double`, `boolean`) only when the member is neither
  nullable nor optional; otherwise boxed (`Long`, `Double`, `Boolean`).
- One top-level class per object path. Class names derive from the key (singularized under `[*]` / `.*`); an explicit
  per-path mapping overrides them. A class-name collision between different paths fails unless the mapping resolves it.
- Keys that are not valid Java identifiers get a camelCase field plus `@JsonProperty("<key>")`.

Decisions made here:

- New sub-package `io.github.pgatzka.codegen`.
- Generated classes carry `@javax.annotation.processing.Generated("io.github.pgatzka.json-pojo-gen")` (JDK annotation).
- The generator needs the configured map paths to tell a map object from an empty object, so `GeneratorOptions`
  carries `mapPaths`.
- Formatting of generated code is not byte-exact; tests compile the output and reflect on it.

## Package and class names

| Kind | Fully qualified name | Source set | Notes |
|------|----------------------|------------|-------|
| record | `io.github.pgatzka.codegen.GeneratorOptions` | `plugin/src/main/java` | new |
| record | `io.github.pgatzka.codegen.JavaFile` | `plugin/src/main/java` | new |
| class | `io.github.pgatzka.codegen.PojoGenerator` | `plugin/src/main/java` | new; static utility, not instantiable |
| test | `io.github.pgatzka.codegen.GeneratorOptionsTest` | `plugin/src/test/java` | new |
| test | `io.github.pgatzka.codegen.JavaFileTest` | `plugin/src/test/java` | new |
| test | `io.github.pgatzka.codegen.PojoGeneratorTest` | `plugin/src/test/java` | new; compiles generated code with `javax.tools` |

Nothing outside the new package changes.

## Public API (exact Java signatures)

```java
package io.github.pgatzka.codegen;

import java.util.Map;
import java.util.Set;

/**
 * Options for {@link PojoGenerator}.
 * @param packageName                Java package of all generated classes, e.g. "com.acme.model"; never null
 * @param rootClassName              class name for the root object; never null
 * @param mapPaths                   collapsed paths of objects treated as maps (same set given to JsonPathLister); never null
 * @param classNames                 collapsed object path → class name overrides; never null, may be empty
 * @param generateEqualsAndHashCode  whether to emit equals(Object) and hashCode()
 * @param generateToString           whether to emit toString()
 */
public record GeneratorOptions(
        String packageName,
        String rootClassName,
        Set<String> mapPaths,
        Map<String, String> classNames,
        boolean generateEqualsAndHashCode,
        boolean generateToString) {

    /**
     * Compact constructor. Throws NullPointerException for any null argument or null map/set element; copies
     * mapPaths and classNames into unmodifiable collections. Throws IllegalArgumentException when:
     * packageName is not a sequence of Java identifiers separated by '.', none of them a keyword
     * (message starts with "Invalid package name"); rootClassName or any classNames value is not a Java identifier,
     * is a keyword, or is one of var/yield/record/sealed/permits (message starts with "Invalid class name");
     * rootClassName or any classNames value is a reserved class name per the "Reserved class names" rule
     * (message starts with "Class name <X> is reserved"); any classNames key or mapPaths element does not start
     * with '$' (message starts with "Invalid path").
     */

    /** Equivalent to {@code new GeneratorOptions(packageName, rootClassName, Set.of(), Map.of(), true, true)}. */
    public static GeneratorOptions defaults(String packageName, String rootClassName);
}
```

```java
package io.github.pgatzka.codegen;

/**
 * One generated Java source file.
 * @param packageName the package, e.g. "com.acme.model"; never null
 * @param className   the simple class name; never null
 * @param content     the full source text, UTF-8, ends with a newline; never null
 */
public record JavaFile(String packageName, String className, String content) {

    /** Compact constructor: NullPointerException on any null component. */

    /** {@code packageName.replace('.', '/') + "/" + className + ".java"}, e.g. "com/acme/model/Product.java". */
    public String relativePath();
}
```

```java
package io.github.pgatzka.codegen;

import io.github.pgatzka.TypedJsonPath;
import java.util.List;

/** Generates mutable Java POJOs from the typed path list of one JSON document. Not instantiable. */
public final class PojoGenerator {

    // private constructor

    /**
     * Generates one {@link JavaFile} per JSON object in {@code paths}: the root class first, then one class per
     * object path in first-occurrence order. Returns an unmodifiable list; never null.
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the root is not an object (message starts with "Root must be a JSON object");
     *         if two different object paths resolve to the same class name (message starts with "Class name <X> used by both");
     *         if a class name is reserved (message starts with "Class name <X> is reserved");
     *         if a classNames key is not an object path of the document (message starts with "classNames path not found");
     *         if a class name cannot be derived from a key (message starts with "Cannot derive class name for");
     *         if field or accessor names collide (messages start with "Field name" / "Accessor name");
     *         if a path in the list is malformed (message starts with "Invalid path")
     */
    public static List<JavaFile> generate(List<TypedJsonPath> paths, GeneratorOptions options);
}
```

## Generation rules

### Structure

- The input is exactly what `JsonPathLister.listPaths(json, mapPaths)` returns for one document. The root is an object
  when the list is empty or every path starts with `$.` or `$[`'...'`]`. A list containing `$[*]` means the root is an
  array → `IllegalArgumentException("Root must be a JSON object...")`. `mapPaths` containing `$` (root as a map) →
  `IllegalArgumentException("Root must be a JSON object, not a map path")`. An empty list produces one empty root class.
- A **member path** is a path whose last segment is `.name` or `['name']`; its parent is the path without that segment
  (`$` for root members). The **key** is the segment's name with `['...']` unescaped (`\'` → `'`, `\\` → `\`).
- An **object path** is a path with type `object` that is not in `mapPaths`, plus the root `$`. Every object path becomes
  one class containing one field per member path whose parent is that object path, in list order.
- A path in `mapPaths` is a map; its members are never listed (the lister collapsed them to `.*`).

### Java type of a path P with type T, flags `nullable` / `optional`

| Case | Java type |
|---|---|
| P ∈ `mapPaths` | `Map<String, V>` where V is the reference type of `P.*`, or `Object` if `P.*` is absent (empty map) |
| T = `array` | `List<E>` where E is the reference type of `P[*]`, or `Object` if absent (only empty arrays) |
| T = `object` | the class name of P |
| T = `string` | `String` |
| T = `boolean` | `boolean`, or `Boolean` when boxed |
| T = `integer` | `long`, or `Long` when boxed |
| T = `float` | `double`, or `Double` when boxed |
| T = `null` | `Object` |

"Boxed" applies when P is a member path with `nullable || optional`, **or** when P is used as a generic argument
(`[*]` and `.*` paths are always reference types). The reference type of a primitive is its wrapper.

### Field names

- If the key is a valid Java identifier (`Character.isJavaIdentifierStart` for the first char, `isJavaIdentifierPart`
  for the rest) and not a Java keyword or literal (`class`, `int`, `null`, `true`, …), the field name is the key as is
  (`ä` and `_ok9` qualify).
- Otherwise: split the key on runs of characters that are not Java identifier parts, drop empty parts, lower-case the
  first character of the first part and upper-case the first character of each following part, join
  (`first name` → `firstName`, `zip-code` → `zipCode`, `a b c` → `aBC`). A result that is empty (key `---`) becomes `__`
  (`_` alone is a keyword since Java 9); a result starting with a character that is not an identifier start gets `_`
  prefixed (`1st` → `_1st`); a result that is a keyword gets `_` appended (`class` → `class_`).
- The empty key `""` (lister path `['']`) cannot be mapped: `@JsonProperty("")` means "default name" to Jackson. It is
  rejected with `IllegalArgumentException`, message starts with `Empty JSON key at <parent path>`.
- The names `Objects`, `Double` and `java` are treated like keywords (`_` appended: `Objects_`, `Double_`, `java_`),
  because a field with one of these names would shadow the `java.util.Objects.equals/hash`, `Double.compare` and
  `java.util...` qualifiers the generated `equals`/`hashCode` use.
- Jackson derives the property name from the getter, not from the field: it strips `get`/`is` and lower-cases the whole
  leading run of upper-case characters (`getFirstName` → `firstName`, `getId` → `id`, `getURL` → `url`,
  `getURLValue` → `urlvalue`, `getAB` → `ab`, `get_1st` → `_1st`, `isActive` → `active`). Whenever that derived name
  differs from the key, **the getter and the setter** are annotated `@JsonProperty("<key>")`, with the key escaped as a
  Java string literal (`"` → `\"`, `\` → `\\`); the field itself is never annotated (an annotated private field would be
  a second, separately named property for keys such as `Id`, and serialization would emit both `Id` and `id`). So keys
  `Id`, `URL` and `aB` are valid identifiers kept as field names but still get annotated accessors.
- Two members of one object whose field names collide (e.g. keys `a b` and `a-b`) → `IllegalArgumentException`,
  message starts with `Field name <f> used by both`.
- Two members whose accessor names collide although the field names differ (keys `a` and `A` both give `getA`) →
  `IllegalArgumentException`, message starts with `Accessor name <getX> used by both`. A member whose capitalized name is
  `Class` would produce `getClass()`, which clashes with `Object.getClass()` → `IllegalArgumentException`, message starts
  with `Field name Class conflicts with Object.getClass()`.

### Class names

For an object path P:

1. `classNames.get(P)` if present.
2. Else `rootClassName` if P is `$`.
3. Else take the key of the enclosing member: strip trailing `[*]` and `.*` segments (`$.items[*]` → `items`,
   `$.matrix[*][*]` → `matrix`, `$.translations.*` → `translations`), and if at least one segment was stripped,
   singularize: `ies` → `y`; a word ending in `ses`, `xes`, `zes`, `ches` or `shes` loses `es`; a word ending in `s`
   but not `ss` loses `s`; anything else unchanged (`items` → `item`, `addresses` → `address`, `data` → `data`,
   `status` → `statu` is accepted as the price of simple rules; use `classNames` to fix it).
4. Convert to UpperCamelCase like field names but with the first character upper-cased; `_` prefix if it starts with a
   non-identifier-start, keyword → `_` appended. An empty result → `IllegalArgumentException("Cannot derive class name for <P>...")`.

Any two distinct object paths that resolve to the same class name (including the root) →
`IllegalArgumentException`, message starts with `Class name <X> used by both <P1> and <P2>`. Names that differ only in
case (`Item` / `ITEM`) also collide, because their files would overwrite each other on case-insensitive file systems:
`IllegalArgumentException`, message starts with `Class name <X> collides with <Y>` and contains both paths. Every
`classNames` key must be an object path of the document, else `IllegalArgumentException("classNames path not found: <P>")`.

**Reserved class names.** A class name (derived, `rootClassName` or a `classNames` value) must not be the simple name
of a public type in `java.lang` (`Object`, `String`, `Long`, `Double`, `Boolean`, `Record`, `Thread`, …; check with
`Class.forName("java.lang." + name)`), nor one of the names the generated code imports: `List`, `Map`, `Objects`,
`JsonProperty`, `Generated`. Derived names that hit this rule (`"objects": [...]` → `Object`, `"strings"` → `String`)
→ `IllegalArgumentException`, message starts with `Class name <X> is reserved`; the user picks another via `classNames`.
Configured names that hit it are rejected by `GeneratorOptions` with the same message prefix.

**Keywords.** Besides `javax.lang.model.SourceVersion.isKeyword`, the restricted identifiers `var`, `yield`, `record`,
`sealed` and `permits` are not legal type names and are rejected as class names (`Invalid class name` in
`GeneratorOptions`; `Class name <X> is reserved` when derived).

### Class content

Each `JavaFile.content` is, in this order (blank lines and indentation are free; tests compile, they do not diff):

1. `package <packageName>;`
2. Imports, only those used, in this order: `com.fasterxml.jackson.annotation.JsonProperty`, `java.util.List`,
   `java.util.Map`, `java.util.Objects`, `javax.annotation.processing.Generated`.
3. `@Generated("io.github.pgatzka.json-pojo-gen")` then `public class <Name> {`.
4. Fields: `private <Type> <field>;` in member order, never annotated.
5. `public <Name>() {}`.
6. For each field, a getter and a setter: getter `public <Type> get<Field>()`, except a primitive `boolean` field gets
   `public boolean is<Field>()`; setter `public void set<Field>(<Type> <field>)`. `<Field>` is the field name with its
   first character upper-cased (`firstName` → `FirstName`, `_1st` → `_1st`). When the member needs `@JsonProperty`
   (see "Field names"), both the getter and the setter carry it.
7. If `generateEqualsAndHashCode`: `public boolean equals(Object o)` returning true for the same instance, false unless
   `o instanceof <Name>`, then comparing every field: `long`/`boolean` with `==`, `double` with `Double.compare(a, b) == 0`,
   reference fields with `Objects.equals`; and `public int hashCode()` returning `Objects.hash(<all fields>)`.
   Both annotated `@Override`.
8. If `generateToString`: `public String toString()` returning `"<Name>{" + "f1=" + f1 + ", f2=" + f2 + ... + "}"`
   (fields in order, `, ` separated; an empty class returns `"<Name>{}"`). Annotated `@Override`.
9. Closing brace and a final newline.

Reference input (same document as the README sample):

```json
{
  "id": 1,
  "name": "widget",
  "price": 9.5,
  "active": true,
  "first name": "x",
  "address": {"city": "Berlin", "zip": "10115"},
  "tags": ["a", "b"],
  "items": [
    {"sku": "s1", "qty": 2, "note": null, "attrs": {"color": "red"}},
    {"sku": "s2", "qty": 2.5, "note": "x", "price": 1.5, "attrs": {"size": 3}}
  ],
  "translations": {"en": {"text": "Hello", "plural": false}, "de": {"text": "Hallo"}},
  "none": null
}
```

with `mapPaths = {"$.translations", "$.items[*].attrs"}`, `packageName = "com.acme.model"`, `rootClassName = "Product"`,
no `classNames`, both flags true, generates exactly four files in this order: `Product`, `Address`, `Item`, `Translation`,
with these fields (name: type):

| Class | Fields |
|---|---|
| `Product` | `id: long`, `name: String`, `price: double`, `active: boolean`, `firstName: String` (getter and setter `@JsonProperty("first name")`), `address: Address`, `tags: List<String>`, `items: List<Item>`, `translations: Map<String, Translation>`, `none: Object` |
| `Address` | `city: String`, `zip: String` |
| `Item` | `sku: String`, `qty: double`, `note: String`, `attrs: Map<String, String>`, `price: Double` (list order: `attrs` first appears in item 1, `price` only in item 2) |
| `Translation` | `text: String`, `plural: Boolean` |

## Behavior / acceptance criteria

Tests obtain the input list with `JsonPathLister.listPaths(json, mapPaths)` (the real lister), compile generated files
with `javax.tools.ToolProvider.getSystemJavaCompiler()` into a `@TempDir` using the test JVM's `java.class.path` as the
compile classpath (it contains `jackson-annotations`), load them with a `URLClassLoader`, and reflect.

1. Reference input: `generate` returns four files whose `className`s are `Product`, `Address`, `Item`, `Translation` in
   that order, all with `packageName` `com.acme.model` and `relativePath()` `com/acme/model/<Name>.java`.
2. The four files compile without errors.
3. Reflection on the compiled classes shows exactly the fields of the table above (names, declared generic types via
   `Field.getGenericType().getTypeName()`, e.g. `java.util.List<java.lang.String>`, `java.util.Map<java.lang.String, com.acme.model.Translation>`,
   primitives as `long`/`double`/`boolean`), all `private`, in declaration order.
4. `Product.getFirstName()` and `Product.setFirstName(String)` carry `@JsonProperty` with value `first name`; no field of
   the four classes carries `@JsonProperty`, and no other method does.
5. Every class has a public no-arg constructor and, per field, a public getter and setter with the declared type:
   `getId()/setId(long)`, `isActive()/setActive(boolean)`, `getFirstName()/setFirstName(String)`, `getPrice()` on `Item`
   returning `Double`, etc.
6. Every class is annotated `@javax.annotation.processing.Generated` with value `io.github.pgatzka.json-pojo-gen`.
7. End to end with Jackson: `new ObjectMapper().readValue(referenceJson, productClass)` succeeds; via getters,
   `id == 1`, `firstName.equals("x")`, `items.size() == 2`, the second item's `getPrice()` is `1.5` and the first item's
   is `null`, `translations.get("de").getPlural()` is `null`, `translations.get("en").getPlural()` is `Boolean.FALSE`,
   `attrs` of the second item maps `"size"` to `"3"`.
8. `equals`/`hashCode`: two `Address` instances with the same `city`/`zip` are equal and have the same `hashCode`;
   differing `zip` → not equal; an instance is not equal to `null` or to an `Item`. With
   `generateEqualsAndHashCode == false` the class declares neither method (check `getDeclaredMethod` throws
   `NoSuchMethodException`), and `Objects` is not imported.
9. `toString`: an `Address` with city `Berlin` and zip `10115` prints `Address{city=Berlin, zip=10115}`; with
   `generateToString == false` the class declares no `toString`.
10. Boxing: for `{"a":1,"b":null,"c":[{"x":true},{}],"d":[1,2]}` the root has `a: long`, `b: Object`, `c: List<C>`,
    `d: List<Long>`; class `C` has `x: Boolean` (optional) with getter `getX()` returning `Boolean`.
11. Field naming: for `{"first name":1,"zip-code":2,"class":3,"1st":4,"ä":5,"_ok9":6}` the root fields are
    `firstName`, `zipCode`, `class_`, `_1st`, `ä`, `_ok9`; exactly the getters and setters of the first four carry
    `@JsonProperty` with values `first name`, `zip-code`, `class`, `1st`; getters are `getFirstName`, `getZipCode`,
    `getClass_`, `get_1st`, `getÄ`, `get_ok9`. Note `getÄ` → Jackson name `ä` (lower-cased leading run), equal to the key,
    so no annotation.
18. Jackson naming: for `{"Id":1,"URL":"u","aB":2,"ok":3}` the fields are `Id`, `URL`, `aB`, `ok`; the getters and setters
    of `Id`, `URL` and `aB` carry `@JsonProperty("Id")`, `@JsonProperty("URL")`, `@JsonProperty("aB")`; `ok`'s accessors have
    none; `new ObjectMapper().readValue(json, rootClass)` succeeds with `getId() == 1`, `getURL().equals("u")`,
    `getAB() == 2`; and the round trip `mapper.readTree(mapper.writeValueAsString(obj))` equals `mapper.readTree(json)`
    (no duplicate `id`/`Id` properties).
21. Shadowing names: for `{"Objects":1,"Double":2.5,"java":"x","ok":true}` the fields are `Objects_` (`long`), `Double_`
    (`double`), `java_` (`String`), `ok` (`boolean`); the accessors of the first three carry `@JsonProperty` with the
    original keys; the class compiles with both flags on, and `readValue` of that document followed by `writeValueAsString`
    round-trips to an equal tree.
19. Reserved names: `{"objects":[{"a":1}]}` → `IllegalArgumentException` starting with `Class name Object is reserved`;
    `{"strings":[{"a":1}]}` → `Class name String is reserved`; `{"lists":[{"a":1}]}` → `Class name List is reserved`;
    with `classNames = {"$.objects[*]": "Thing"}` the first input generates `Root` and `Thing`.
    `GeneratorOptions.defaults("com.acme", "Object")` → `Class name Object is reserved`;
    `defaults("com.acme", "record")`, `defaults("com.acme", "var")` → `Invalid class name`.
20. Accessor collisions: `{"a":1,"A":2}` → `IllegalArgumentException` starting with `Accessor name getA used by both`;
    `{"Class":1}` → `IllegalArgumentException` starting with `Field name Class conflicts with Object.getClass()`.
12. Class naming: for `{"addresses":[{"a":1}],"data":[{"b":1}],"categories":[{"c":1}],"boxes":[{"d":1}],"matrix":[[{"e":1}]]}`
    the classes are `Root` (rootClassName), `Address`, `Data`, `Category`, `Box`, `Matrix` in that order.
13. `classNames` override: same input as 12 with `classNames = {"$.data[*]": "Datum"}` names that class `Datum`;
    `classNames = {"$.nope": "X"}` → `IllegalArgumentException` starting with `classNames path not found`.
14. Collision: `{"billing":{"address":{"a":1}},"shipping":{"address":{"b":2}}}` → `IllegalArgumentException` whose message
    starts with `Class name Address used by both` and contains both `$.billing.address` and `$.shipping.address`;
    with `classNames = {"$.shipping.address": "ShippingAddress"}` generation succeeds and yields classes
    `Root`, `Billing`, `Address`, `Shipping`, `ShippingAddress`.
15. Maps: `{"m":{}}` with `mapPaths = {"$.m"}` → root field `m: Map<String, Object>`; `{"m":{"a":{"x":1}}}` with
    `mapPaths = {"$.m"}` → `m: Map<String, M>` and a class `M` with `x: long`. `{"e":[]}` → `e: List<Object>`.
16. Empty root `{}` → one file, class `Root`, no fields, compiles, `toString()` returns `Root{}`, two instances are equal.
17. `GeneratorOptions.defaults("com.acme", "Root")` has empty `mapPaths` and `classNames` and both flags true;
    `mapPaths()` and `classNames()` of any instance are unmodifiable.

## Edge cases and error handling

- `generate(null, options)` and `generate(paths, null)` → `NullPointerException`.
- Root array: `listPaths("[{\"a\":1}]")` as input → `IllegalArgumentException` starting with `Root must be a JSON object`.
- `new GeneratorOptions("com.acme", "Root", null, Map.of(), true, true)` → `NullPointerException`; same for a null `classNames`.
- `GeneratorOptions.defaults("com.9acme", "Root")`, `defaults("com.class", "Root")`, `defaults("", "Root")` → `IllegalArgumentException`
  starting with `Invalid package name`; `defaults("com.acme", "9Root")`, `defaults("com.acme", "class")`,
  `defaults("com.acme", "")` → `IllegalArgumentException` starting with `Invalid class name`; a `classNames` value `"my class"`
  → `Invalid class name`; a `classNames` key or `mapPaths` element `"items"` (no leading `$`) → `Invalid path`.
- Field collision: `{"a b":1,"a-b":2}` → `IllegalArgumentException` starting with `Field name aB used by both`.
- Empty key: `{"":1}` → `IllegalArgumentException` starting with `Empty JSON key at $`; `{"a":{"":1}}` → starting with
  `Empty JSON key at $.a`.
- Root as map: `listPaths("{\"a\":{\"x\":1}}", Set.of("$"))` with options whose `mapPaths` is `{"$"}` →
  `IllegalArgumentException` starting with `Root must be a JSON object, not a map path`.
- Case-only class collision: `{"item":{"a":1},"ITEM":{"b":1}}` → `IllegalArgumentException` starting with `Class name`
  and containing `collides with`, `$.item` and `$.ITEM`.
- Empty camelCase result: `{"---":1}` → field `__` with accessors `get__`/`set__` annotated `@JsonProperty("---")`; compiles.
- Malformed path in the input list → `IllegalArgumentException` starting with `Invalid path`, for each of:
  `foo` (no `$`), `$.a..b` (empty segment), `$['x` (unterminated bracket), `$.a[0]` (index instead of `[*]`),
  each given as a single `TypedJsonPath(..., JsonType.STRING)` in the list.
- Root collision: `{"root":{"a":1}}` with `rootClassName = "Root"` → `IllegalArgumentException` starting with
  `Class name Root used by both`.
- A `null`-typed path inside an array (`[null]` under `$.x`) → `x: List<Object>`.
- Nested arrays `{"matrix":[[1]]}` → `matrix: List<List<Long>>`.
- `new JavaFile(null, "A", "x")` → `NullPointerException`; `relativePath()` for package `a.b` and class `C` is `a/b/C.java`.

## Out of scope

- The Gradle task, extension properties and source-set wiring (next spec).
- Records, builders, `Optional<T>`, `@Nullable`, Lombok, Javadoc on generated members, sample values as comments.
- Merging identical shapes into one class; ignore rules; renaming fields via configuration.
- Any serialization annotations beyond `@JsonProperty`; `@JsonIgnoreProperties`.
- Formatting guarantees of the generated source beyond compiling.
