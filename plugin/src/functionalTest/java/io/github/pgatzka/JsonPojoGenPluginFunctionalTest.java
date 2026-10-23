package io.github.pgatzka;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Functional tests for the {@code io.github.pgatzka.json-pojo-gen} plugin.
 */
class JsonPojoGenPluginFunctionalTest {

    @TempDir
    Path projectDir;

    private static final String REFERENCE_DOCUMENT = """
            {
              "id": 1,
              "name": "widget",
              "address": {"city": "Berlin", "zip": "10115"},
              "tags": ["a", "b"],
              "items": [{"sku": "s1", "qty": 2}, {"sku": "s2", "price": 9.5}],
              "empty": {},
              "none": null
            }
            """;

    private static final List<String> REFERENCE_PATHS = List.of(
            "$.id",
            "$.name",
            "$.address",
            "$.address.city",
            "$.address.zip",
            "$.tags",
            "$.tags[*]",
            "$.items",
            "$.items[*]",
            "$.items[*].sku",
            "$.items[*].qty",
            "$.items[*].price",
            "$.empty",
            "$.none");

    private static final String CONFIGURED_BUILD_SCRIPT = "plugins {\n"
            + "    id(\"io.github.pgatzka.json-pojo-gen\")\n"
            + "}\n"
            + "jsonPojoGen {\n"
            + "    inputFile.set(file(\"input.json\"))\n"
            + "}\n";

    private static final String UNCONFIGURED_BUILD_SCRIPT = "plugins {\n"
            + "    id(\"io.github.pgatzka.json-pojo-gen\")\n"
            + "}\n";

    private void writeProject(String buildScript, String inputJson) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("build.gradle.kts"), buildScript, StandardCharsets.UTF_8);
        if (inputJson != null) {
            Files.writeString(projectDir.resolve("input.json"), inputJson, StandardCharsets.UTF_8);
        }
    }

    private GradleRunner listJsonPathsRunner() {
        return GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withArguments("listJsonPaths")
                .withProjectDir(projectDir.toFile());
    }

    private static List<String> dollarLines(BuildResult result) {
        return result.getOutput().lines()
                .map(String::strip)
                .filter(line -> line.startsWith("$"))
                .toList();
    }

    @Test
    @DisplayName("3: running the removed placeholder task fails with task not found")
    void removedPlaceholderTask_failsWithTaskNotFound() throws IOException {
        writeProject(UNCONFIGURED_BUILD_SCRIPT, null);

        BuildResult result = GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withArguments("greeting")
                .withProjectDir(projectDir.toFile())
                .buildAndFail();

        assertThat(result.getOutput()).contains("Task 'greeting' not found");
    }

    @Test
    @DisplayName("list-json-paths 10: listJsonPaths succeeds on the reference document")
    void listJsonPaths_referenceDocument_finishesWithSuccess() throws IOException {
        writeProject(CONFIGURED_BUILD_SCRIPT, REFERENCE_DOCUMENT);

        BuildResult result = listJsonPathsRunner().build();

        BuildTask task = result.task(":listJsonPaths");
        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    @DisplayName("list-json-paths 10: listJsonPaths logs exactly the 14 paths, one per line, in order")
    void listJsonPaths_referenceDocument_logsExpectedPathsInOrder() throws IOException {
        writeProject(CONFIGURED_BUILD_SCRIPT, REFERENCE_DOCUMENT);

        BuildResult result = listJsonPathsRunner().build();

        List<String> strippedLines = result.getOutput().lines().map(String::strip).toList();
        assertThat(strippedLines).containsSubsequence(REFERENCE_PATHS);
        assertThat(dollarLines(result)).containsExactlyElementsOf(REFERENCE_PATHS);
    }

    @Test
    @DisplayName("Edge: listJsonPaths fails on invalid JSON")
    void listJsonPaths_invalidJson_fails() throws IOException {
        writeProject(CONFIGURED_BUILD_SCRIPT, "{\"a\":");

        BuildResult result = listJsonPathsRunner().buildAndFail();

        BuildTask task = result.task(":listJsonPaths");
        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(TaskOutcome.FAILED);
        assertThat(result.getOutput()).contains("Invalid JSON in ");
    }

    @Test
    @DisplayName("Edge: listJsonPaths fails when no inputFile is configured")
    void listJsonPaths_noInputFileConfigured_fails() throws IOException {
        writeProject(UNCONFIGURED_BUILD_SCRIPT, null);

        BuildResult result = listJsonPathsRunner().buildAndFail();

        assertThat(result.getOutput()).contains("property 'inputFile'");
    }

    @Test
    @DisplayName("Edge: listJsonPaths succeeds on a scalar-root document and logs no paths")
    void listJsonPaths_scalarRoot_succeedsWithoutPathLines() throws IOException {
        writeProject(CONFIGURED_BUILD_SCRIPT, "42");

        BuildResult result = listJsonPathsRunner().build();

        BuildTask task = result.task(":listJsonPaths");
        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dollarLines(result)).isEmpty();
    }
}
