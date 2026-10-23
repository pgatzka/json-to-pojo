package io.github.pgatzka;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Logs the JSON paths found in the configured input file, one per line, at lifecycle level.
 */
public abstract class ListJsonPathsTask extends DefaultTask {

    /** Name under which the plugin registers this task. Value: "listJsonPaths". Never null. */
    public static final String NAME = "listJsonPaths";

    /** Task group. Value: "json-pojo-gen". Never null. */
    public static final String GROUP = "json-pojo-gen";

    /**
     * Creates the task; Gradle instantiates it as a managed object.
     */
    public ListJsonPathsTask() {
    }

    /**
     * Returns the JSON file to read; defaults (via convention) to the extension's {@code inputFile}.
     *
     * @return the input file property; never null
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputFile();

    /**
     * Reads {@code inputFile}, computes its JSON paths with {@link JsonPathLister#listPaths(java.nio.file.Path)}
     * and logs each path as its own lifecycle message.
     *
     * @throws IOException if the file cannot be read
     * @throws GradleException if the file is not valid JSON; message starts with "Invalid JSON in " followed by
     *         the file's absolute path; the IllegalArgumentException is the cause
     */
    @TaskAction
    public void listPaths() throws IOException {
        File file = getInputFile().get().getAsFile();
        List<String> paths;
        try {
            paths = JsonPathLister.listPaths(file.toPath());
        } catch (IllegalArgumentException e) {
            throw new GradleException("Invalid JSON in " + file.getAbsolutePath() + ": " + e.getMessage(), e);
        }
        for (String path : paths) {
            getLogger().lifecycle("{}", path);
        }
    }
}
