# Feature: <Feature name>

<!--
Copy this file to docs/specs/<feature-slug>.md and fill in every section.
This spec is the ONLY shared contract between the test-writer and the implementer.
The test-writer never sees src/main; the implementer never sees src/test.
If a signature is ambiguous here, the two agents will diverge and the build goes red.
-->

## Context

<!-- Why this feature exists. One or two paragraphs. Link related specs with relative paths. -->

## Package and class names

| Kind | Fully qualified name | Source set | Notes |
|------|----------------------|------------|-------|
| class | `io.github.pgatzka.Foo` | `plugin/src/main/java` | new / modified |
| test  | `io.github.pgatzka.FooTest` | `plugin/src/test/java` | unit test |
| test  | `io.github.pgatzka.FooFunctionalTest` | `plugin/src/functionalTest/java` | only if Gradle TestKit is needed |

<!-- Base package is io.github.pgatzka (sub-packages allowed). Name every type the tests will reference, including exceptions, records, enums and nested types. -->

## Public API (exact Java signatures)

<!--
Exact, compilable Java. Include: modifiers, generics, parameter names, checked exceptions,
return types, constructors, static factories, constants. Nothing else is public.
Mark nullability in words ("never null", "may be null") and whether returned collections are unmodifiable.
-->

```java
package io.github.pgatzka;

public final class Foo {

    /** <one-line contract> */
    public Foo(String name);

    /** <one-line contract> */
    public String name();

    /** <one-line contract>; throws IllegalArgumentException if ... */
    public static Foo parse(String input);
}
```

## Behavior / acceptance criteria

<!--
Numbered, each independently testable. Use concrete inputs and expected outputs.
Each criterion should map to at least one test method. Refer to criteria by number in reviews.
-->

1. Given `...`, when `...`, then `...`.
2. ...
3. ...

## Edge cases and error handling

<!--
Null, empty, whitespace, boundary values, concurrency, encoding, filesystem failures.
State the exact exception type and, where tests should assert it, the message or message fragment.
-->

- `null` input to `parse` throws `NullPointerException` (message not asserted).
- Empty input throws `IllegalArgumentException` with a message containing `"empty"`.
- ...

## Out of scope

<!-- What this spec deliberately does not cover, so neither agent "helpfully" adds it. -->

- ...
