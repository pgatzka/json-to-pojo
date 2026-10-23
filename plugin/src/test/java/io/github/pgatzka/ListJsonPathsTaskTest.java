package io.github.pgatzka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ListJsonPathsTask}.
 */
class ListJsonPathsTaskTest {

    @TempDir
    Path tempDir;

    private Project newProjectWithPlugin() throws IOException {
        Path projectDir = Files.createDirectories(tempDir.resolve("project"));
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPlugins().apply(JsonPojoGenPlugin.class);
        return project;
    }

    private static ListJsonPathsTask task(Project project) {
        return project.getTasks().named(ListJsonPathsTask.NAME, ListJsonPathsTask.class).get();
    }

    private static JsonPojoGenExtension extension(Project project) {
        return project.getExtensions().getByType(JsonPojoGenExtension.class);
    }

    private File writeFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file.toFile();
    }

    @Test
    @DisplayName("9: NAME is 'listJsonPaths'")
    void name_isListJsonPaths() {
        assertThat(ListJsonPathsTask.NAME).isEqualTo("listJsonPaths");
    }

    @Test
    @DisplayName("9: GROUP is 'json-pojo-gen'")
    void group_isJsonPojoGen() {
        assertThat(ListJsonPathsTask.GROUP).isEqualTo("json-pojo-gen");
    }

    @Test
    @DisplayName("11: task inputFile defaults to the extension's inputFile")
    void inputFile_conventionFollowsExtension() throws IOException {
        Project project = newProjectWithPlugin();
        File someFile = writeFile("some.json", "{}");

        extension(project).getInputFile().set(someFile);

        assertThat(task(project).getInputFile().get().getAsFile()).isEqualTo(someFile);
    }

    @Test
    @DisplayName("11: setting the task's inputFile overrides the extension value")
    void inputFile_explicitTaskValueOverridesExtension() throws IOException {
        Project project = newProjectWithPlugin();
        File someFile = writeFile("some.json", "{}");
        File otherFile = writeFile("other.json", "{}");
        ListJsonPathsTask task = task(project);

        extension(project).getInputFile().set(someFile);
        task.getInputFile().set(otherFile);

        assertThat(task.getInputFile().get().getAsFile()).isEqualTo(otherFile);
    }

    @Test
    @DisplayName("11: task inputFile is not present when nothing is configured")
    void inputFile_notPresentWithoutConfiguration() throws IOException {
        Project project = newProjectWithPlugin();

        assertThat(task(project).getInputFile()).isNotNull();
        assertThat(task(project).getInputFile().isPresent()).isFalse();
    }

    @Test
    @DisplayName("12: listPaths completes for a valid JSON file")
    void listPaths_validJson_completesWithoutException() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("input.json", "{\"a\":[1]}");
        ListJsonPathsTask task = task(project);
        task.getInputFile().set(input);

        assertThatCode(task::listPaths).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("12: listPaths completes when the file comes from the extension")
    void listPaths_inputFromExtension_completesWithoutException() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("input.json", "{\"a\":[1]}");
        extension(project).getInputFile().set(input);

        assertThatCode(task(project)::listPaths).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Edge: scalar-root document completes without exception")
    void listPaths_scalarRoot_completesWithoutException() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("scalar.json", "42");
        ListJsonPathsTask task = task(project);
        task.getInputFile().set(input);

        assertThatCode(task::listPaths).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Edge: invalid JSON throws GradleException naming the file")
    void listPaths_invalidJson_throwsGradleException() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("broken.json", "{\"a\":");
        ListJsonPathsTask task = task(project);
        task.getInputFile().set(input);

        assertThatThrownBy(task::listPaths)
                .isInstanceOf(GradleException.class)
                .hasMessageStartingWith("Invalid JSON in ")
                .hasMessageContaining(input.getAbsolutePath())
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Edge: non-existent input file throws IOException")
    void listPaths_missingFile_throwsIOException() throws IOException {
        Project project = newProjectWithPlugin();
        ListJsonPathsTask task = task(project);
        task.getInputFile().set(tempDir.resolve("does-not-exist.json").toFile());

        assertThatThrownBy(task::listPaths).isInstanceOf(IOException.class);
    }

    // ---------------------------------------------------------------------------------------------
    // map-paths (docs/specs/map-paths.md)
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("map-paths: task mapPaths is never null and empty by default")
    void mapPaths_emptyByDefault() throws IOException {
        Project project = newProjectWithPlugin();

        assertThat(task(project).getMapPaths()).isNotNull();
        assertThat(task(project).getMapPaths().get()).isEmpty();
    }

    @Test
    @DisplayName("map-paths 12: listPaths with a map path completes without exception")
    void listPaths_withMapPath_completesWithoutException() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("map.json", "{\"a\":{\"b\":1}}");
        ListJsonPathsTask task = task(project);
        task.getInputFile().set(input);
        task.getMapPaths().set(Set.of("$.a"));

        assertThatCode(task::listPaths).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("map-paths 12: listPaths with a map path from the extension completes without exception")
    void listPaths_withMapPathFromExtension_completesWithoutException() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("map.json", "{\"a\":{\"b\":1}}");
        extension(project).getInputFile().set(input);
        extension(project).getMapPaths().add("$.a");

        assertThatCode(task(project)::listPaths).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("map-paths Edge: unmatched map path throws GradleException naming the file")
    void listPaths_unmatchedMapPath_throwsGradleException() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("input.json", "{\"a\":1}");
        ListJsonPathsTask task = task(project);
        task.getInputFile().set(input);
        task.getMapPaths().set(Set.of("$.nope"));

        assertThatThrownBy(task::listPaths)
                .isInstanceOf(GradleException.class)
                .hasMessageStartingWith("Map path error for ")
                .hasMessageContaining(input.getAbsolutePath())
                .hasMessageContaining("Map path $.nope not found")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("map-paths Edge: map path error message is file path, ': ', then the lister's message")
    void listPaths_unmatchedMapPath_messageFormat() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("input.json", "{\"a\":1}");
        ListJsonPathsTask task = task(project);
        task.getInputFile().set(input);
        task.getMapPaths().set(Set.of("$.nope"));

        assertThatThrownBy(task::listPaths)
                .isInstanceOf(GradleException.class)
                .hasMessageStartingWith("Map path error for " + input.getAbsolutePath() + ": Map path $.nope not found");
    }

    @Test
    @DisplayName("map-paths Edge: map path to an array throws GradleException")
    void listPaths_mapPathToArray_throwsGradleException() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("input.json", "{\"a\":[1]}");
        ListJsonPathsTask task = task(project);
        task.getInputFile().set(input);
        task.getMapPaths().set(Set.of("$.a"));

        assertThatThrownBy(task::listPaths)
                .isInstanceOf(GradleException.class)
                .hasMessageStartingWith("Map path error for ")
                .hasMessageContaining(input.getAbsolutePath())
                .hasMessageContaining("Map path $.a does not point to a JSON object")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("map-paths Edge: syntactically invalid map path throws GradleException")
    void listPaths_invalidMapPathSyntax_throwsGradleException() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("input.json", "{\"items\":{\"a\":1}}");
        ListJsonPathsTask task = task(project);
        task.getInputFile().set(input);
        task.getMapPaths().set(Set.of("items"));

        assertThatThrownBy(task::listPaths)
                .isInstanceOf(GradleException.class)
                .hasMessageStartingWith("Map path error for ")
                .hasMessageContaining(input.getAbsolutePath())
                .hasMessageContaining("Invalid map path")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("map-paths Edge: invalid JSON with map paths still throws the unchanged Invalid JSON error")
    void listPaths_invalidJsonWithMapPaths_throwsInvalidJsonGradleException() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("broken.json", "{\"a\":");
        ListJsonPathsTask task = task(project);
        task.getInputFile().set(input);
        task.getMapPaths().set(Set.of("$.a"));

        assertThatThrownBy(task::listPaths)
                .isInstanceOf(GradleException.class)
                .hasMessageStartingWith("Invalid JSON in ")
                .hasMessageContaining(input.getAbsolutePath())
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
