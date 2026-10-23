# Feature: Remove the scaffold `greeting` task

## Context

`gradle init` generated a `greeting` task that prints a hello line. It survived the package migration
(`docs/specs/package-migration.md`) as a placeholder. The plugin now has a real task (`listJsonPaths`,
`docs/specs/list-json-paths.md`), so the placeholder goes away: the task, its name constant and its tests.

## Package and class names

| Kind | Fully qualified name | Source set | Notes |
|------|----------------------|------------|-------|
| class | `io.github.pgatzka.JsonPojoGenPlugin` | `plugin/src/main/java` | modified: `GREETING_TASK_NAME` and the task registration removed |
| test  | `io.github.pgatzka.JsonPojoGenPluginTest` | `plugin/src/test/java` | modified: greeting tests removed, criterion 2 added; all other tests kept |
| test  | `io.github.pgatzka.JsonPojoGenPluginFunctionalTest` | `plugin/src/functionalTest/java` | modified: greeting tests and the `runGreeting` helper removed, criterion 3 added; `listJsonPaths` tests kept |

## Public API (exact Java signatures)

```java
package io.github.pgatzka;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** Gradle plugin entry point for json-pojo-gen. */
public class JsonPojoGenPlugin implements Plugin<Project> {

    /** The plugin id under which this plugin is published and applied. Value: "io.github.pgatzka.json-pojo-gen". Never null. */
    public static final String PLUGIN_ID = "io.github.pgatzka.json-pojo-gen";

    /** Implicit public no-arg constructor (Gradle instantiates the plugin). */
    public JsonPojoGenPlugin();

    /**
     * Creates the {@code jsonPojoGen} extension and registers the {@code listJsonPaths} task (unchanged from
     * docs/specs/list-json-paths.md). No longer registers any other task.
     * @param project the project the plugin is applied to; never null when called by Gradle
     */
    @Override
    public void apply(Project project);
}
```

The constant `GREETING_TASK_NAME` is deleted. `JsonPojoGenExtension`, `ListJsonPathsTask` and `JsonPathLister` are unchanged.

## Behavior / acceptance criteria

1. Applying the plugin (by id and by class) to a `ProjectBuilder` project still registers the `jsonPojoGen` extension and
   the `listJsonPaths` task exactly as before (existing tests keep passing).
2. After applying the plugin, `project.getTasks().findByName("greeting")` returns `null`, and the set of task names added by
   the plugin contains `listJsonPaths` and does not contain `greeting`.
3. Functional (Gradle TestKit, `withPluginClasspath()`): a project that applies the plugin and runs `greeting` fails
   (`buildAndFail()`), and the output contains `Task 'greeting' not found`.
4. No production file under `plugin/src/main` references `GREETING_TASK_NAME`, `greeting` or `Hello from plugin` any more,
   and no test file references `GREETING_TASK_NAME` or `Hello from plugin` (verified by the reviewer with `git grep`, not by
   a test). The literal `greeting` is allowed in tests only for the absence checks of criteria 2 and 3 and the edge case
   below, and inside the old plugin id `org.example.greeting` in the existing unknown-plugin-id test, which stays. Historical mentions in `docs/specs/package-migration.md` and `CHANGELOG.md` stay.

## Edge cases and error handling

- Applying the plugin twice still registers exactly one `listJsonPaths` task and no `greeting` task.
- No other error handling.

## Out of scope

- Any change to `listJsonPaths`, `JsonPathLister` or the extension.
- Rewriting `docs/specs/package-migration.md`; it documents history.
