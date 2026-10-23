package io.github.pgatzka;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin entry point for json-pojo-gen.
 */
public class JsonPojoGenPlugin implements Plugin<Project> {

    /** The plugin id under which this plugin is published and applied. */
    public static final String PLUGIN_ID = "io.github.pgatzka.json-pojo-gen";

    /**
     * Creates the plugin; Gradle instantiates it reflectively.
     */
    public JsonPojoGenPlugin() {}

    /**
     * Creates the {@code jsonPojoGen} extension and registers the {@code listJsonPaths} task on the given project.
     *
     * @param project the project the plugin is applied to; never null when called by Gradle
     */
    @Override
    public void apply(Project project) {
        JsonPojoGenExtension extension =
                project.getExtensions().create(JsonPojoGenExtension.NAME, JsonPojoGenExtension.class);
        project.getTasks().register(ListJsonPathsTask.NAME, ListJsonPathsTask.class, task -> {
            task.setGroup(ListJsonPathsTask.GROUP);
            task.setDescription("Logs the JSON paths found in the configured input file.");
            task.getInputFile().convention(extension.getInputFile());
            task.getMapPaths().convention(extension.getMapPaths());
        });
    }
}
