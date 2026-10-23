package io.github.pgatzka;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Logs the typed JSON paths found in the configured input file, one per line, at lifecycle level.
 */
@DisableCachingByDefault(because = "Only logs paths and declares no outputs; nothing to cache")
public abstract class ListJsonPathsTask extends DefaultTask {

    /** Name under which the plugin registers this task. Value: "listJsonPaths". Never null. */
    public static final String NAME = "listJsonPaths";

    /** Task group. Value: "json-pojo-gen". Never null. */
    public static final String GROUP = "json-pojo-gen";

    /**
     * Creates the task; Gradle instantiates it as a managed object.
     */
    public ListJsonPathsTask() {}

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
     * Reads {@code inputFile}, computes its JSON paths with
     * {@link JsonPathLister#listPaths(java.nio.file.Path, java.util.Set)} using {@code mapPaths},
     * and logs each typed path ({@code <path>: <type>}) as its own lifecycle message.
     *
     * @throws IOException if the file cannot be read
     * @throws GradleException if the file is not valid JSON (message starts with "Invalid JSON in " followed by
     *         the file's absolute path), if a map path is invalid, not found or does not point to an object
     *         (message starts with "Map path error for " followed by the file's absolute path, then ": " and the
     *         lister's message), or if a path has conflicting types (message starts with "Type conflict in "
     *         followed by the file's absolute path, then ": " and the lister's message); in all cases the
     *         IllegalArgumentException is the cause
     */
    @TaskAction
    public void listPaths() throws IOException {
        File file = getInputFile().get().getAsFile();
        Set<String> mapPaths = getMapPaths().getOrElse(Set.of());
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
        for (TypedJsonPath path : paths) {
            getLogger().lifecycle("{}", path);
        }
    }
}
