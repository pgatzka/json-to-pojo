package io.github.pgatzka;

import javax.lang.model.SourceVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

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
     * Creates the {@code jsonPojoGen} extension with its conventions, registers the {@code listJsonPaths} and
     * {@code generateJsonPojos} tasks and, once the {@code java} plugin is applied, adds the generated sources to
     * the {@code main} source set.
     *
     * @param project the project the plugin is applied to; never null when called by Gradle
     */
    @Override
    public void apply(Project project) {
        JsonPojoGenExtension extension =
                project.getExtensions().create(JsonPojoGenExtension.NAME, JsonPojoGenExtension.class);
        extension.getGenerateEqualsAndHashCode().convention(true);
        extension.getGenerateToString().convention(true);
        extension
                .getOutputDirectory()
                .convention(project.getLayout().getBuildDirectory().dir("generated/sources/jsonPojoGen/java/main"));
        extension
                .getRootClassName()
                .convention(extension
                        .getInputFile()
                        .map(file -> rootClassName(file.getAsFile().getName())));

        project.getTasks().register(ListJsonPathsTask.NAME, ListJsonPathsTask.class, task -> {
            task.setGroup(ListJsonPathsTask.GROUP);
            task.setDescription("Logs the JSON paths found in the configured input file.");
            task.getInputFile().convention(extension.getInputFile());
            task.getMapPaths().convention(extension.getMapPaths());
        });

        TaskProvider<GenerateJsonPojosTask> generate = project.getTasks()
                .register(GenerateJsonPojosTask.NAME, GenerateJsonPojosTask.class, task -> {
                    task.setGroup(GenerateJsonPojosTask.GROUP);
                    task.setDescription("Generates Java POJOs from the configured JSON file.");
                    task.getInputFile().convention(extension.getInputFile());
                    task.getMapPaths().convention(extension.getMapPaths());
                    task.getPackageName().convention(extension.getPackageName());
                    task.getRootClassName().convention(extension.getRootClassName());
                    task.getClassNames().convention(extension.getClassNames());
                    task.getGenerateEqualsAndHashCode().convention(extension.getGenerateEqualsAndHashCode());
                    task.getGenerateToString().convention(extension.getGenerateToString());
                    task.getOutputDirectory().convention(extension.getOutputDirectory());
                });

        // withPlugin also fires when java is applied after this plugin, so the order in plugins {} does not matter.
        project.getPluginManager()
                .withPlugin(
                        "java",
                        plugin -> project.getExtensions()
                                .getByType(JavaPluginExtension.class)
                                .getSourceSets()
                                .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                                .getJava()
                                .srcDir(generate.flatMap(GenerateJsonPojosTask::getOutputDirectory)));
    }

    /**
     * Derives the root class name from a file name with the generator's key-to-class-name rules. An empty result
     * becomes {@code _} so the convention never fails at configuration time; the generator reports it later.
     */
    private static String rootClassName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
        StringBuilder result = new StringBuilder();
        StringBuilder part = new StringBuilder();
        base.codePoints().forEach(cp -> {
            if (Character.isJavaIdentifierPart(cp)) {
                part.appendCodePoint(cp);
            } else {
                appendCapitalized(result, part);
            }
        });
        appendCapitalized(result, part);
        if (result.isEmpty()) {
            return "_";
        }
        if (!Character.isJavaIdentifierStart(result.codePointAt(0))) {
            result.insert(0, '_');
        }
        if (SourceVersion.isKeyword(result)) {
            result.append('_');
        }
        return result.toString();
    }

    private static void appendCapitalized(StringBuilder target, StringBuilder part) {
        if (part.isEmpty()) {
            return;
        }
        int first = part.codePointAt(0);
        target.appendCodePoint(Character.toUpperCase(first)).append(part, Character.charCount(first), part.length());
        part.setLength(0);
    }
}
