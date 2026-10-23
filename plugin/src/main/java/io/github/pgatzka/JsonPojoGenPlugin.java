package io.github.pgatzka;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin entry point for json-pojo-gen.
 */
public class JsonPojoGenPlugin implements Plugin<Project> {

    /** The plugin id under which this plugin is published and applied. */
    public static final String PLUGIN_ID = "io.github.pgatzka.json-pojo-gen";

    /** Name of the task this plugin registers. */
    public static final String GREETING_TASK_NAME = "greeting";

    /**
     * Creates the plugin; Gradle instantiates it reflectively.
     */
    public JsonPojoGenPlugin() {
    }

    /**
     * Registers the {@code greeting} task, the {@code jsonPojoGen} extension and the {@code listJsonPaths} task
     * on the given project.
     *
     * @param project the project the plugin is applied to; never null when called by Gradle
     */
    @Override
    public void apply(Project project) {
        project.getTasks().register(GREETING_TASK_NAME, task ->
                task.doLast(t -> System.out.println("Hello from plugin '" + PLUGIN_ID + "'")));

        JsonPojoGenExtension extension =
                project.getExtensions().create(JsonPojoGenExtension.NAME, JsonPojoGenExtension.class);
        project.getTasks().register(ListJsonPathsTask.NAME, ListJsonPathsTask.class, task -> {
            task.setGroup(ListJsonPathsTask.GROUP);
            task.setDescription("Logs the JSON paths found in the configured input file.");
            task.getInputFile().convention(extension.getInputFile());
        });
    }
}
