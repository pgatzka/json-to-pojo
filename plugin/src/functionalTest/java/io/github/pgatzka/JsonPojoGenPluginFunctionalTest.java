package io.github.pgatzka;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private BuildResult runGreeting() throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("build.gradle.kts"),
                "plugins {\n"
                        + "    id(\"io.github.pgatzka.json-pojo-gen\")\n"
                        + "}\n",
                StandardCharsets.UTF_8);

        return GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withArguments("greeting")
                .withProjectDir(projectDir.toFile())
                .build();
    }

    @Test
    @DisplayName("4: greeting task succeeds")
    void greeting_finishesWithSuccess() throws IOException {
        BuildResult result = runGreeting();

        BuildTask task = result.task(":greeting");
        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    @DisplayName("4: greeting task prints the hello line with the new plugin id")
    void greeting_printsHelloLineWithNewPluginId() throws IOException {
        BuildResult result = runGreeting();

        assertThat(result.getOutput().lines())
                .anyMatch(line -> line.strip().equals("Hello from plugin 'io.github.pgatzka.json-pojo-gen'"));
        assertThat(result.getOutput()).doesNotContain("org.example.greeting");
    }
}
