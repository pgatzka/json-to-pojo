package io.github.pgatzka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.gradle.api.Project;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JsonPojoGenPlugin}.
 */
class JsonPojoGenPluginTest {

    private static Set<String> taskNamesAddedBy(Consumer<Project> applyPlugin) {
        Project project = ProjectBuilder.builder().build();
        Set<String> before = new HashSet<>(project.getTasks().getNames());

        applyPlugin.accept(project);

        Set<String> added = new HashSet<>(project.getTasks().getNames());
        added.removeAll(before);
        return added;
    }

    @Test
    @DisplayName("1: applying by id registers the extension and the listJsonPaths task")
    void applyById_registersExtensionAndListJsonPathsTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");

        assertThat(project.getExtensions().getByName("jsonPojoGen")).isInstanceOf(JsonPojoGenExtension.class);
        assertThat(project.getTasks().findByName("listJsonPaths")).isInstanceOf(ListJsonPathsTask.class);
    }

    @Test
    @DisplayName("1: applying by class registers the extension and the listJsonPaths task")
    void applyByClass_registersExtensionAndListJsonPathsTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(JsonPojoGenPlugin.class);

        assertThat(project.getExtensions().getByName("jsonPojoGen")).isInstanceOf(JsonPojoGenExtension.class);
        assertThat(project.getTasks().findByName("listJsonPaths")).isInstanceOf(ListJsonPathsTask.class);
    }

    @Test
    @DisplayName("2: applying by id does not register the removed placeholder task")
    void applyById_doesNotRegisterPlaceholderTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");

        assertThat(project.getTasks().findByName("greeting")).isNull();
    }

    @Test
    @DisplayName("2: applying by class does not register the removed placeholder task")
    void applyByClass_doesNotRegisterPlaceholderTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(JsonPojoGenPlugin.class);

        assertThat(project.getTasks().findByName("greeting")).isNull();
    }

    @Test
    @DisplayName("2: task names added by applying by id contain listJsonPaths but not the placeholder")
    void applyById_addedTaskNamesContainListJsonPathsOnly() {
        Set<String> added = taskNamesAddedBy(project -> project.getPlugins().apply("io.github.pgatzka.json-pojo-gen"));

        assertThat(added).contains("listJsonPaths").doesNotContain("greeting");
    }

    @Test
    @DisplayName("2: task names added by applying by class contain listJsonPaths but not the placeholder")
    void applyByClass_addedTaskNamesContainListJsonPathsOnly() {
        Set<String> added = taskNamesAddedBy(project -> project.getPlugins().apply(JsonPojoGenPlugin.class));

        assertThat(added).contains("listJsonPaths").doesNotContain("greeting");
    }

    @Test
    @DisplayName("package-migration 1: applying by id makes the plugin instance available")
    void applyById_pluginIsJsonPojoGenPlugin() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");

        assertThat(project.getPlugins().hasPlugin(JsonPojoGenPlugin.class)).isTrue();
    }

    @Test
    @DisplayName("package-migration 3: PLUGIN_ID has the published plugin id")
    void pluginId_isIoGithubPgatzkaJsonPojoGen() {
        assertThat(JsonPojoGenPlugin.PLUGIN_ID).isEqualTo("io.github.pgatzka.json-pojo-gen");
    }

    @Test
    @DisplayName("package-migration 3: applying by PLUGIN_ID registers the listJsonPaths task")
    void applyByPluginIdConstant_registersListJsonPathsTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(JsonPojoGenPlugin.PLUGIN_ID);

        assertThat(project.getTasks().findByName("listJsonPaths")).isInstanceOf(ListJsonPathsTask.class);
    }

    @Test
    @DisplayName("package-migration 5: old plugin id org.example.greeting is unknown")
    void applyOldId_throwsUnknownPluginException() {
        Project project = ProjectBuilder.builder().build();

        assertThatThrownBy(() -> project.getPlugins().apply("org.example.greeting"))
                .isInstanceOf(UnknownPluginException.class);
    }

    @Test
    @DisplayName("Edge: applying twice by id leaves exactly one listJsonPaths task and no placeholder task")
    void applyTwiceById_leavesOneListJsonPathsTaskAndNoPlaceholder() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");
        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");

        assertThat(project.getTasks().getNames()).filteredOn("listJsonPaths"::equals).hasSize(1);
        assertThat(project.getTasks().findByName("greeting")).isNull();
    }

    @Test
    @DisplayName("Edge: applying by id then by class leaves exactly one listJsonPaths task and no placeholder task")
    void applyByIdThenByClass_leavesOneListJsonPathsTaskAndNoPlaceholder() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");
        project.getPlugins().apply(JsonPojoGenPlugin.class);

        assertThat(project.getTasks().getNames()).filteredOn("listJsonPaths"::equals).hasSize(1);
        assertThat(project.getTasks().findByName("greeting")).isNull();
    }

    @Test
    @DisplayName("list-json-paths 9: extension NAME is 'jsonPojoGen'")
    void extensionName_isJsonPojoGen() {
        assertThat(JsonPojoGenExtension.NAME).isEqualTo("jsonPojoGen");
    }

    @Test
    @DisplayName("list-json-paths 9: applying by id registers the jsonPojoGen extension")
    void applyById_registersJsonPojoGenExtension() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");

        assertThat(project.getExtensions().getByName("jsonPojoGen")).isInstanceOf(JsonPojoGenExtension.class);
    }

    @Test
    @DisplayName("list-json-paths 9: applying by class registers the jsonPojoGen extension")
    void applyByClass_registersJsonPojoGenExtension() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(JsonPojoGenPlugin.class);

        assertThat(project.getExtensions().getByName("jsonPojoGen")).isInstanceOf(JsonPojoGenExtension.class);
    }

    @Test
    @DisplayName("list-json-paths 9: extension inputFile has no default value")
    void applyByClass_extensionInputFileHasNoDefault() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(JsonPojoGenPlugin.class);

        JsonPojoGenExtension extension = project.getExtensions().getByType(JsonPojoGenExtension.class);
        assertThat(extension.getInputFile()).isNotNull();
        assertThat(extension.getInputFile().isPresent()).isFalse();
    }

    @Test
    @DisplayName("list-json-paths 9: applying by id registers the listJsonPaths task")
    void applyById_registersListJsonPathsTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");

        assertThat(project.getTasks().findByName("listJsonPaths")).isInstanceOf(ListJsonPathsTask.class);
    }

    @Test
    @DisplayName("list-json-paths 9: applying by class registers the listJsonPaths task")
    void applyByClass_registersListJsonPathsTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(JsonPojoGenPlugin.class);

        assertThat(project.getTasks().findByName("listJsonPaths")).isInstanceOf(ListJsonPathsTask.class);
    }

    @Test
    @DisplayName("list-json-paths 9: listJsonPaths task has group json-pojo-gen")
    void applyByClass_listJsonPathsTaskHasGroup() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(JsonPojoGenPlugin.class);

        assertThat(project.getTasks().getByName("listJsonPaths").getGroup()).isEqualTo("json-pojo-gen");
    }

    @Test
    @DisplayName("list-json-paths 9: listJsonPaths task has the specified description")
    void applyByClass_listJsonPathsTaskHasDescription() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(JsonPojoGenPlugin.class);

        assertThat(project.getTasks().getByName("listJsonPaths").getDescription())
                .isEqualTo("Logs the JSON paths found in the configured input file.");
    }

    // ---------------------------------------------------------------------------------------------
    // map-paths (docs/specs/map-paths.md)
    // ---------------------------------------------------------------------------------------------

    private static Project projectWithPlugin() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply(JsonPojoGenPlugin.class);
        return project;
    }

    private static JsonPojoGenExtension extensionOf(Project project) {
        return project.getExtensions().getByType(JsonPojoGenExtension.class);
    }

    private static ListJsonPathsTask listJsonPathsTaskOf(Project project) {
        return project.getTasks().named(ListJsonPathsTask.NAME, ListJsonPathsTask.class).get();
    }

    @Test
    @DisplayName("map-paths 9: extension and task mapPaths are empty after applying the plugin")
    void applyByClass_mapPathsEmptyByDefault() {
        Project project = projectWithPlugin();

        assertThat(extensionOf(project).getMapPaths()).isNotNull();
        assertThat(extensionOf(project).getMapPaths().isPresent()).isTrue();
        assertThat(extensionOf(project).getMapPaths().get()).isEmpty();
        assertThat(listJsonPathsTaskOf(project).getMapPaths().get()).isEmpty();
    }

    @Test
    @DisplayName("map-paths 9: task mapPaths follows the extension's mapPaths")
    void applyByClass_taskMapPathsConventionFollowsExtension() {
        Project project = projectWithPlugin();

        extensionOf(project).getMapPaths().add("$.m");

        assertThat(listJsonPathsTaskOf(project).getMapPaths().get()).containsExactly("$.m");
    }

    @Test
    @DisplayName("map-paths 9: setting task mapPaths overrides the extension value")
    void applyByClass_explicitTaskMapPathsOverrideExtension() {
        Project project = projectWithPlugin();
        ListJsonPathsTask task = listJsonPathsTaskOf(project);

        extensionOf(project).getMapPaths().add("$.m");
        task.getMapPaths().set(Set.of("$.n"));

        assertThat(task.getMapPaths().get()).containsExactly("$.n");
    }

    @Test
    @DisplayName("map-paths 9: applying by id wires the mapPaths convention too")
    void applyById_taskMapPathsConventionFollowsExtension() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");

        extensionOf(project).getMapPaths().add("$.m");

        assertThat(listJsonPathsTaskOf(project).getMapPaths().get()).containsExactly("$.m");
    }
}
