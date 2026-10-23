package io.github.pgatzka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.gradle.api.Project;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JsonPojoGenPlugin}.
 */
class JsonPojoGenPluginTest {

    @Test
    @DisplayName("1: applying by id registers the greeting task")
    void applyById_registersGreetingTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");

        assertThat(project.getTasks().findByName("greeting")).isNotNull();
    }

    @Test
    @DisplayName("1: applying by id makes the plugin instance available")
    void applyById_pluginIsJsonPojoGenPlugin() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");

        assertThat(project.getPlugins().hasPlugin(JsonPojoGenPlugin.class)).isTrue();
    }

    @Test
    @DisplayName("2: applying by class registers the greeting task")
    void applyByClass_registersGreetingTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(JsonPojoGenPlugin.class);

        assertThat(project.getTasks().findByName("greeting")).isNotNull();
    }

    @Test
    @DisplayName("2: applying by class registers task under GREETING_TASK_NAME")
    void applyByClass_registersTaskUnderConstantName() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(JsonPojoGenPlugin.class);

        assertThat(project.getTasks().findByName(JsonPojoGenPlugin.GREETING_TASK_NAME)).isNotNull();
    }

    @Test
    @DisplayName("3: PLUGIN_ID has the published plugin id")
    void pluginId_isIoGithubPgatzkaJsonPojoGen() {
        assertThat(JsonPojoGenPlugin.PLUGIN_ID).isEqualTo("io.github.pgatzka.json-pojo-gen");
    }

    @Test
    @DisplayName("3: GREETING_TASK_NAME is 'greeting'")
    void greetingTaskName_isGreeting() {
        assertThat(JsonPojoGenPlugin.GREETING_TASK_NAME).isEqualTo("greeting");
    }

    @Test
    @DisplayName("3: applying by PLUGIN_ID registers the greeting task")
    void applyByPluginIdConstant_registersGreetingTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply(JsonPojoGenPlugin.PLUGIN_ID);

        assertThat(project.getTasks().findByName(JsonPojoGenPlugin.GREETING_TASK_NAME)).isNotNull();
    }

    @Test
    @DisplayName("5: old plugin id org.example.greeting is unknown")
    void applyOldId_throwsUnknownPluginException() {
        Project project = ProjectBuilder.builder().build();

        assertThatThrownBy(() -> project.getPlugins().apply("org.example.greeting"))
                .isInstanceOf(UnknownPluginException.class);
    }

    @Test
    @DisplayName("Edge: applying twice by id leaves exactly one greeting task")
    void applyTwiceById_leavesExactlyOneGreetingTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");
        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");

        assertThat(project.getTasks().findByName("greeting")).isNotNull();
        assertThat(project.getTasks().getNames()).filteredOn("greeting"::equals).hasSize(1);
    }

    @Test
    @DisplayName("Edge: applying by id then by class leaves exactly one greeting task")
    void applyByIdThenByClass_leavesExactlyOneGreetingTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");
        project.getPlugins().apply(JsonPojoGenPlugin.class);

        assertThat(project.getTasks().findByName("greeting")).isNotNull();
        assertThat(project.getTasks().getNames()).filteredOn("greeting"::equals).hasSize(1);
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

    @Test
    @DisplayName("list-json-paths 9: greeting task is still registered alongside listJsonPaths")
    void applyById_registersBothGreetingAndListJsonPaths() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");

        assertThat(project.getTasks().findByName("greeting")).isNotNull();
        assertThat(project.getTasks().findByName("listJsonPaths")).isNotNull();
    }

    @Test
    @DisplayName("list-json-paths 9: applying twice leaves exactly one listJsonPaths task")
    void applyTwice_leavesExactlyOneListJsonPathsTask() {
        Project project = ProjectBuilder.builder().build();

        project.getPlugins().apply("io.github.pgatzka.json-pojo-gen");
        project.getPlugins().apply(JsonPojoGenPlugin.class);

        assertThat(project.getTasks().getNames()).filteredOn("listJsonPaths"::equals).hasSize(1);
    }
}
