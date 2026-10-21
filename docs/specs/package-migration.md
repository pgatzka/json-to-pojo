# Feature: Migrate scaffold from `org.example` to `io.github.pgatzka`

## Context

The project was created with `gradle init`, which generated a placeholder plugin in package `org.example` with plugin id
`org.example.greeting`. The project's base package is `io.github.pgatzka` and the plugin id is `io.github.pgatzka.json-pojo-gen`.
This spec moves the existing scaffold to the correct package and id and removes every trace of `org.example` from the source tree.
Behavior stays the same (a `greeting` task that prints a hello line); only names change.

## Package and class names

| Kind | Fully qualified name | Source set | Notes |
|------|----------------------|------------|-------|
| class | `io.github.pgatzka.JsonPojoGenPlugin` | `plugin/src/main/java` | moved from `org.example.JsonPojoGenPlugin`; old file deleted |
| test  | `io.github.pgatzka.JsonPojoGenPluginTest` | `plugin/src/test/java` | replaces `org.example.JsonPojoGenPluginTest`; old file deleted |
| test  | `io.github.pgatzka.JsonPojoGenPluginFunctionalTest` | `plugin/src/functionalTest/java` | replaces `org.example.JsonPojoGenPluginFunctionalTest`; old file deleted |

After this change, **no file may remain under any `org/example` directory** in `plugin/src/main`, `plugin/src/test` or `plugin/src/functionalTest`.
Each agent deletes the old files in its own source set(s): the implementer deletes the one under `src/main`, the test-writer deletes the two under
`src/test` and `src/functionalTest`.

## Public API (exact Java signatures)

```java
package io.github.pgatzka;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** Gradle plugin entry point for json-pojo-gen. */
public class JsonPojoGenPlugin implements Plugin<Project> {

    /** The plugin id under which this plugin is published and applied. Value: "io.github.pgatzka.json-pojo-gen". Never null. */
    public static final String PLUGIN_ID = "io.github.pgatzka.json-pojo-gen";

    /** Name of the task this plugin registers. Value: "greeting". Never null. */
    public static final String GREETING_TASK_NAME = "greeting";

    /** Implicit public no-arg constructor (Gradle instantiates the plugin). */
    public JsonPojoGenPlugin();

    /**
     * Registers the {@code greeting} task on the given project.
     * @param project the project the plugin is applied to; never null when called by Gradle
     */
    @Override
    public void apply(Project project);
}
```

Build script contract (`plugin/build.gradle.kts`, changed by the implementer):

```kotlin
gradlePlugin {
    val jsonPojoGen by plugins.creating {
        id = "io.github.pgatzka.json-pojo-gen"
        implementationClass = "io.github.pgatzka.JsonPojoGenPlugin"
    }
}
```

## Behavior / acceptance criteria

1. Applying the plugin by id to a `ProjectBuilder` project (`project.getPlugins().apply("io.github.pgatzka.json-pojo-gen")`)
   results in `project.getTasks().findByName("greeting")` returning a non-null task.
2. Applying the plugin by class (`project.getPlugins().apply(JsonPojoGenPlugin.class)`) has the same effect as criterion 1.
3. `JsonPojoGenPlugin.PLUGIN_ID` equals `"io.github.pgatzka.json-pojo-gen"` and `JsonPojoGenPlugin.GREETING_TASK_NAME` equals `"greeting"`.
4. Functional (Gradle TestKit, `GradleRunner.withPluginClasspath()`): a project whose build script contains
   `plugins { id("io.github.pgatzka.json-pojo-gen") }` and that runs the `greeting` task finishes with task outcome `SUCCESS`
   for `:greeting`, and the build output contains the exact line `Hello from plugin 'io.github.pgatzka.json-pojo-gen'`.
5. The plugin id `org.example.greeting` no longer exists: applying it by id throws `org.gradle.api.plugins.UnknownPluginException`
   (unit test with `ProjectBuilder`).
6. No source file remains under `plugin/src/main/java/org/example`, `plugin/src/test/java/org/example` or
   `plugin/src/functionalTest/java/org/example` (verified by the reviewer, not by a test).

## Edge cases and error handling

- Applying the plugin twice to the same project does not throw and still leaves exactly one task named `greeting` (Gradle plugin application is idempotent;
  a test may assert `findByName("greeting")` is non-null after two `apply` calls).
- No other error handling; `apply` is only ever called by Gradle with a non-null project.

## Out of scope

- Renaming or removing the `greeting` task, or changing what it prints beyond the plugin id inside the quotes.
- Any JSON-to-POJO functionality.
- Changing the Gradle plugin publishing setup, the `rootProject.name`, or the subproject name.
