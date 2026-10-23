package io.github.pgatzka;

import io.github.pgatzka.codegen.GeneratorOptions;
import io.github.pgatzka.codegen.JavaFile;
import io.github.pgatzka.codegen.PojoGenerator;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Generates Java POJOs from the configured JSON file into the output directory. */
@CacheableTask
public abstract class GenerateJsonPojosTask extends DefaultTask {

    /** Name under which the plugin registers this task. Value: "generateJsonPojos". Never null. */
    public static final String NAME = "generateJsonPojos";

    /** Task group. Value: "json-pojo-gen". Never null. */
    public static final String GROUP = "json-pojo-gen";

    /**
     * Creates the task; Gradle instantiates it as a managed object.
     */
    public GenerateJsonPojosTask() {}

    /**
     * Returns the JSON file to read; defaults (via convention) to the extension's {@code inputFile}.
     *
     * @return the input file property; never null
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputFile();

    /**
     * Returns the JSON paths of objects to treat as maps; defaults (via convention) to the extension's
     * {@code mapPaths}.
     *
     * @return the map paths property; never null
     */
    @Input
    public abstract SetProperty<String> getMapPaths();

    /**
     * Returns the Java package of the generated classes; defaults (via convention) to the extension's
     * {@code packageName}.
     *
     * @return the package name property; never null
     */
    @Input
    public abstract Property<String> getPackageName();

    /**
     * Returns the class name of the root object; defaults (via convention) to the extension's
     * {@code rootClassName}.
     *
     * @return the root class name property; never null
     */
    @Input
    public abstract Property<String> getRootClassName();

    /**
     * Returns the object path to class name overrides; defaults (via convention) to the extension's
     * {@code classNames}.
     *
     * @return the class names property; never null
     */
    @Input
    public abstract MapProperty<String, String> getClassNames();

    /**
     * Returns whether equals/hashCode are generated; defaults (via convention) to the extension's
     * {@code generateEqualsAndHashCode}.
     *
     * @return the flag property; never null
     */
    @Input
    public abstract Property<Boolean> getGenerateEqualsAndHashCode();

    /**
     * Returns whether toString is generated; defaults (via convention) to the extension's
     * {@code generateToString}.
     *
     * @return the flag property; never null
     */
    @Input
    public abstract Property<Boolean> getGenerateToString();

    /**
     * Returns the directory the generated sources are written to; defaults (via convention) to the extension's
     * {@code outputDirectory}. The task owns this directory: its contents are replaced on every run.
     *
     * @return the output directory property; never null
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Deletes the contents of the output directory, lists the paths of the input file with the configured map
     * paths, generates the classes with {@link PojoGenerator} and writes each file (UTF-8) at its
     * {@link JavaFile#relativePath()} below the output directory. Logs one lifecycle line:
     * {@code Generated <n> Java file(s) into <absolute output dir>}.
     *
     * @throws IOException if reading or writing fails
     * @throws GradleException with the same messages as {@link ListJsonPathsTask} for lister failures
     *         ("Invalid JSON in &lt;file&gt;: ...", "Map path error for &lt;file&gt;: ...", "Type conflict in
     *         &lt;file&gt;: ..."), and "Code generation failed for &lt;file&gt;: &lt;message&gt;" for an
     *         IllegalArgumentException from GeneratorOptions or PojoGenerator; the original exception is the cause.
     *         &lt;file&gt; is the input file's absolute path.
     */
    @TaskAction
    public void generate() throws IOException {
        File file = getInputFile().get().getAsFile();
        Path outputDir = getOutputDirectory().get().getAsFile().toPath().toAbsolutePath();
        Set<String> mapPaths = getMapPaths().getOrElse(Set.of());
        Map<String, String> classNames = getClassNames().getOrElse(Map.of());

        clean(outputDir);

        List<TypedJsonPath> paths;
        try {
            paths = JsonPathLister.listPaths(file.toPath(), mapPaths);
        } catch (IllegalArgumentException e) {
            String prefix;
            if (JsonPathLister.isMapPathError(e)) {
                prefix = "Map path error for ";
            } else if (JsonPathLister.isTypeConflict(e)) {
                prefix = "Type conflict in ";
            } else {
                prefix = "Invalid JSON in ";
            }
            throw new GradleException(prefix + file.getAbsolutePath() + ": " + e.getMessage(), e);
        }

        List<JavaFile> files;
        try {
            GeneratorOptions options = new GeneratorOptions(
                    getPackageName().get(),
                    getRootClassName().get(),
                    mapPaths,
                    classNames,
                    getGenerateEqualsAndHashCode().get(),
                    getGenerateToString().get());
            files = PojoGenerator.generate(paths, options);
        } catch (IllegalArgumentException e) {
            throw new GradleException(
                    "Code generation failed for " + file.getAbsolutePath() + ": " + e.getMessage(), e);
        }

        for (JavaFile javaFile : files) {
            Path target = outputDir.resolve(javaFile.relativePath());
            Files.createDirectories(target.getParent());
            Files.writeString(target, javaFile.content(), StandardCharsets.UTF_8);
        }
        getLogger().lifecycle("Generated {} Java file(s) into {}", files.size(), outputDir);
    }

    /** Empties {@code dir} (creating it if missing) so no stale class from an earlier run survives. */
    private static void clean(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            List<Path> entries;
            try (Stream<Path> walk = Files.walk(dir)) {
                entries = walk.filter(p -> !p.equals(dir))
                        .sorted(Comparator.reverseOrder())
                        .toList();
            }
            for (Path entry : entries) {
                Files.delete(entry);
            }
        }
        Files.createDirectories(dir);
    }
}
