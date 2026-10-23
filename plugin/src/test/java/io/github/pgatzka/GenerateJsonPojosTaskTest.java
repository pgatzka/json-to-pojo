package io.github.pgatzka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link GenerateJsonPojosTask}.
 */
class GenerateJsonPojosTaskTest {

    /** Reference input of docs/specs/pojo-generator.md ("Generation rules" -> "Reference input"). */
    private static final String PRODUCT_JSON = """
            {
              "id": 1,
              "name": "widget",
              "price": 9.5,
              "active": true,
              "first name": "x",
              "address": {"city": "Berlin", "zip": "10115"},
              "tags": ["a", "b"],
              "items": [
                {"sku": "s1", "qty": 2, "note": null, "attrs": {"color": "red"}},
                {"sku": "s2", "qty": 2.5, "note": "x", "price": 1.5, "attrs": {"size": 3}}
              ],
              "translations": {"en": {"text": "Hello", "plural": false}, "de": {"text": "Hallo"}},
              "none": null
            }
            """;

    private static final String[] REFERENCE_MAP_PATHS = {"$.translations", "$.items[*].attrs"};

    private static final List<String> REFERENCE_FILES = List.of(
            "com/acme/model/Product.java",
            "com/acme/model/Address.java",
            "com/acme/model/Item.java",
            "com/acme/model/Translation.java");

    @TempDir
    Path tempDir;

    private Project newProjectWithPlugin() throws IOException {
        Path projectDir = Files.createDirectories(tempDir.resolve("project"));
        Project project =
                ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPlugins().apply(JsonPojoGenPlugin.class);
        return project;
    }

    private static GenerateJsonPojosTask task(Project project) {
        return project.getTasks()
                .named(GenerateJsonPojosTask.NAME, GenerateJsonPojosTask.class)
                .get();
    }

    private static JsonPojoGenExtension extension(Project project) {
        return project.getExtensions().getByType(JsonPojoGenExtension.class);
    }

    private File writeFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file.toFile();
    }

    /**
     * Configures input file, map paths and package {@code com.acme.model} on the extension and the output directory on
     * the task; the root class name comes from the input file name by convention.
     */
    private static GenerateJsonPojosTask configuredTask(
            Project project, File input, Path outputDir, String... mapPaths) {
        JsonPojoGenExtension extension = extension(project);
        extension.getInputFile().set(input);
        extension.getMapPaths().addAll(mapPaths);
        extension.getPackageName().set("com.acme.model");
        GenerateJsonPojosTask task = task(project);
        task.getOutputDirectory().set(outputDir.toFile());
        return task;
    }

    private static List<String> relativeRegularFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace(File.separatorChar, '/'))
                    .toList();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Constants and annotations (Public API)
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("API: NAME is 'generateJsonPojos'")
    void name_isGenerateJsonPojos() {
        assertThat(GenerateJsonPojosTask.NAME).isEqualTo("generateJsonPojos");
    }

    @Test
    @DisplayName("API: GROUP is 'json-pojo-gen'")
    void group_isJsonPojoGen() {
        assertThat(GenerateJsonPojosTask.GROUP).isEqualTo("json-pojo-gen");
    }

    @Test
    @DisplayName("API: class is annotated @CacheableTask")
    void class_isCacheableTask() {
        assertThat(GenerateJsonPojosTask.class.isAnnotationPresent(CacheableTask.class))
                .isTrue();
    }

    @Test
    @DisplayName("API: inputFile is @InputFile with @PathSensitive(NONE)")
    void inputFile_isInputFileWithPathSensitivityNone() throws NoSuchMethodException {
        Method getter = GenerateJsonPojosTask.class.getMethod("getInputFile");

        assertThat(getter.isAnnotationPresent(InputFile.class)).isTrue();
        assertThat(getter.getAnnotation(PathSensitive.class)).isNotNull();
        assertThat(getter.getAnnotation(PathSensitive.class).value()).isEqualTo(PathSensitivity.NONE);
    }

    @Test
    @DisplayName("API: all other inputs are declared @Input")
    void otherProperties_areAnnotatedInput() throws NoSuchMethodException {
        for (String name : List.of(
                "getMapPaths",
                "getPackageName",
                "getRootClassName",
                "getClassNames",
                "getGenerateEqualsAndHashCode",
                "getGenerateToString")) {
            assertThat(GenerateJsonPojosTask.class.getMethod(name).isAnnotationPresent(Input.class))
                    .as(name)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("API: outputDirectory is @OutputDirectory and generate() is the @TaskAction")
    void outputDirectoryAndTaskAction_areAnnotated() throws NoSuchMethodException {
        assertThat(GenerateJsonPojosTask.class
                        .getMethod("getOutputDirectory")
                        .isAnnotationPresent(OutputDirectory.class))
                .isTrue();
        assertThat(GenerateJsonPojosTask.class.getMethod("generate").isAnnotationPresent(TaskAction.class))
                .isTrue();
    }

    // ---------------------------------------------------------------------------------------------
    // 4: task properties follow the extension
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("4: task inputFile follows the extension; an explicit task value wins")
    void inputFile_followsExtensionAndTaskValueOverrides() throws IOException {
        Project project = newProjectWithPlugin();
        File some = writeFile("some.json", "{}");
        File other = writeFile("other.json", "{}");
        GenerateJsonPojosTask task = task(project);

        extension(project).getInputFile().set(some);
        assertThat(task.getInputFile().get().getAsFile()).isEqualTo(some);

        task.getInputFile().set(other);
        assertThat(task.getInputFile().get().getAsFile()).isEqualTo(other);
    }

    @Test
    @DisplayName("4: task mapPaths follows the extension; an explicit task value wins")
    void mapPaths_followsExtensionAndTaskValueOverrides() throws IOException {
        Project project = newProjectWithPlugin();
        GenerateJsonPojosTask task = task(project);

        extension(project).getMapPaths().add("$.m");
        assertThat(task.getMapPaths().get()).containsExactly("$.m");

        task.getMapPaths().set(Set.of("$.n"));
        assertThat(task.getMapPaths().get()).containsExactly("$.n");
    }

    @Test
    @DisplayName("4: task packageName follows the extension; an explicit task value wins")
    void packageName_followsExtensionAndTaskValueOverrides() throws IOException {
        Project project = newProjectWithPlugin();
        GenerateJsonPojosTask task = task(project);

        assertThat(task.getPackageName().isPresent()).isFalse();

        extension(project).getPackageName().set("com.acme.model");
        assertThat(task.getPackageName().get()).isEqualTo("com.acme.model");

        task.getPackageName().set("org.other");
        assertThat(task.getPackageName().get()).isEqualTo("org.other");
    }

    @Test
    @DisplayName("4: task rootClassName follows the extension (including its convention); an explicit task value wins")
    void rootClassName_followsExtensionAndTaskValueOverrides() throws IOException {
        Project project = newProjectWithPlugin();
        GenerateJsonPojosTask task = task(project);

        extension(project).getInputFile().set(project.file("product.json"));
        assertThat(task.getRootClassName().get()).isEqualTo("Product");

        extension(project).getRootClassName().set("Thing");
        assertThat(task.getRootClassName().get()).isEqualTo("Thing");

        task.getRootClassName().set("Other");
        assertThat(task.getRootClassName().get()).isEqualTo("Other");
    }

    @Test
    @DisplayName("4: task classNames follows the extension; an explicit task value wins")
    void classNames_followsExtensionAndTaskValueOverrides() throws IOException {
        Project project = newProjectWithPlugin();
        GenerateJsonPojosTask task = task(project);

        assertThat(task.getClassNames().get()).isEmpty();

        extension(project).getClassNames().put("$.data[*]", "Datum");
        assertThat(task.getClassNames().get()).containsExactly(entry("$.data[*]", "Datum"));

        task.getClassNames().set(Map.of("$.x", "X"));
        assertThat(task.getClassNames().get()).containsExactly(entry("$.x", "X"));
    }

    @Test
    @DisplayName("4: task generateEqualsAndHashCode follows the extension; an explicit task value wins")
    void generateEqualsAndHashCode_followsExtensionAndTaskValueOverrides() throws IOException {
        Project project = newProjectWithPlugin();
        GenerateJsonPojosTask task = task(project);

        assertThat(task.getGenerateEqualsAndHashCode().get()).isTrue();

        extension(project).getGenerateEqualsAndHashCode().set(false);
        assertThat(task.getGenerateEqualsAndHashCode().get()).isFalse();

        task.getGenerateEqualsAndHashCode().set(true);
        assertThat(task.getGenerateEqualsAndHashCode().get()).isTrue();
    }

    @Test
    @DisplayName("4: task generateToString follows the extension; an explicit task value wins")
    void generateToString_followsExtensionAndTaskValueOverrides() throws IOException {
        Project project = newProjectWithPlugin();
        GenerateJsonPojosTask task = task(project);

        assertThat(task.getGenerateToString().get()).isTrue();

        extension(project).getGenerateToString().set(false);
        assertThat(task.getGenerateToString().get()).isFalse();

        task.getGenerateToString().set(true);
        assertThat(task.getGenerateToString().get()).isTrue();
    }

    @Test
    @DisplayName("4: task outputDirectory follows the extension (default and explicit); an explicit task value wins")
    void outputDirectory_followsExtensionAndTaskValueOverrides() throws IOException {
        Project project = newProjectWithPlugin();
        GenerateJsonPojosTask task = task(project);

        Path expectedDefault = project.getLayout()
                .getBuildDirectory()
                .get()
                .getAsFile()
                .toPath()
                .resolve("generated/sources/jsonPojoGen/java/main");
        assertThat(task.getOutputDirectory().get().getAsFile().toPath()).isEqualTo(expectedDefault);

        File fromExtension = tempDir.resolve("ext-out").toFile();
        extension(project).getOutputDirectory().set(fromExtension);
        assertThat(task.getOutputDirectory().get().getAsFile()).isEqualTo(fromExtension);

        File fromTask = tempDir.resolve("task-out").toFile();
        task.getOutputDirectory().set(fromTask);
        assertThat(task.getOutputDirectory().get().getAsFile()).isEqualTo(fromTask);
    }

    // ---------------------------------------------------------------------------------------------
    // 5: generate() writes the four reference classes
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("5: generate() writes exactly the four reference files and nothing else")
    void generate_referenceInput_writesExactlyFourFiles() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("product.json", PRODUCT_JSON);
        Path outputDir = tempDir.resolve("out");
        GenerateJsonPojosTask task = configuredTask(project, input, outputDir, REFERENCE_MAP_PATHS);

        task.generate();

        assertThat(relativeRegularFiles(outputDir)).containsExactlyInAnyOrderElementsOf(REFERENCE_FILES);
    }

    @Test
    @DisplayName("5: every generated file is valid UTF-8 and starts with 'package com.acme.model;'")
    void generate_referenceInput_filesAreUtf8AndStartWithPackage() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("product.json", PRODUCT_JSON);
        Path outputDir = tempDir.resolve("out");
        GenerateJsonPojosTask task = configuredTask(project, input, outputDir, REFERENCE_MAP_PATHS);

        task.generate();

        for (String relative : REFERENCE_FILES) {
            String content = Files.readString(outputDir.resolve(relative), StandardCharsets.UTF_8);
            assertThat(content).as(relative).startsWith("package com.acme.model;");
        }
    }

    @Test
    @DisplayName("5: the root class is named after the input file and the other classes after their keys")
    void generate_referenceInput_classDeclarationsMatchFileNames() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("product.json", PRODUCT_JSON);
        Path outputDir = tempDir.resolve("out");
        GenerateJsonPojosTask task = configuredTask(project, input, outputDir, REFERENCE_MAP_PATHS);

        task.generate();

        Path pkg = outputDir.resolve("com/acme/model");
        for (String name : List.of("Product", "Address", "Item", "Translation")) {
            String content = Files.readString(pkg.resolve(name + ".java"), StandardCharsets.UTF_8);
            assertThat(content).as(name).contains("class " + name);
        }
    }

    @Test
    @DisplayName("5: values set directly on the task (no extension) are used")
    void generate_withTaskOnlyConfiguration_writesFourFiles() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("data/input.json", PRODUCT_JSON);
        Path outputDir = tempDir.resolve("out");
        GenerateJsonPojosTask task = task(project);
        task.getInputFile().set(input);
        task.getMapPaths().set(Set.of(REFERENCE_MAP_PATHS));
        task.getPackageName().set("com.acme.model");
        task.getRootClassName().set("Product");
        task.getOutputDirectory().set(outputDir.toFile());

        task.generate();

        assertThat(relativeRegularFiles(outputDir)).containsExactlyInAnyOrderElementsOf(REFERENCE_FILES);
    }

    @Test
    @DisplayName("Edge: the output directory is created if missing")
    void generate_missingOutputDirectory_isCreated() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("product.json", PRODUCT_JSON);
        Path outputDir = tempDir.resolve("does/not/exist/yet");
        GenerateJsonPojosTask task = configuredTask(project, input, outputDir, REFERENCE_MAP_PATHS);

        task.generate();

        assertThat(outputDir).isDirectory();
        assertThat(relativeRegularFiles(outputDir)).containsExactlyInAnyOrderElementsOf(REFERENCE_FILES);
    }

    // ---------------------------------------------------------------------------------------------
    // 6: stale files are removed
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("6: stale Old.java and unrelated junk.txt in the output directory are deleted")
    void generate_staleFiles_areRemoved() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("product.json", PRODUCT_JSON);
        Path outputDir = tempDir.resolve("out");
        Path old = outputDir.resolve("com/acme/model/Old.java");
        Path junk = outputDir.resolve("junk.txt");
        Files.createDirectories(old.getParent());
        Files.writeString(old, "package com.acme.model; public class Old {}\n", StandardCharsets.UTF_8);
        Files.writeString(junk, "junk", StandardCharsets.UTF_8);
        GenerateJsonPojosTask task = configuredTask(project, input, outputDir, REFERENCE_MAP_PATHS);

        task.generate();

        assertThat(old).doesNotExist();
        assertThat(junk).doesNotExist();
        assertThat(relativeRegularFiles(outputDir)).containsExactlyInAnyOrderElementsOf(REFERENCE_FILES);
    }

    @Test
    @DisplayName("Edge: only the output directory's contents are deleted, never its parent's other contents")
    void generate_doesNotTouchParentOfOutputDirectory() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("product.json", PRODUCT_JSON);
        Path parent = Files.createDirectories(tempDir.resolve("parent"));
        Path sibling = parent.resolve("keep.txt");
        Files.writeString(sibling, "keep", StandardCharsets.UTF_8);
        Path outputDir = Files.createDirectories(parent.resolve("out"));
        GenerateJsonPojosTask task = configuredTask(project, input, outputDir, REFERENCE_MAP_PATHS);

        task.generate();

        assertThat(sibling).exists().hasContent("keep");
        assertThat(outputDir).isDirectory();
    }

    // ---------------------------------------------------------------------------------------------
    // 7: flags
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("7: generateToString=false on the task -> Address.java contains no toString")
    void generate_toStringDisabledOnTask_addressHasNoToString() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("product.json", PRODUCT_JSON);
        Path outputDir = tempDir.resolve("out");
        GenerateJsonPojosTask task = configuredTask(project, input, outputDir, REFERENCE_MAP_PATHS);
        task.getGenerateToString().set(false);

        task.generate();

        String address = Files.readString(outputDir.resolve("com/acme/model/Address.java"), StandardCharsets.UTF_8);
        assertThat(address).doesNotContain("toString");
    }

    @Test
    @DisplayName("7: with default flags Address.java contains toString, equals and hashCode")
    void generate_defaultFlags_addressHasToStringEqualsHashCode() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("product.json", PRODUCT_JSON);
        Path outputDir = tempDir.resolve("out");
        GenerateJsonPojosTask task = configuredTask(project, input, outputDir, REFERENCE_MAP_PATHS);

        task.generate();

        String address = Files.readString(outputDir.resolve("com/acme/model/Address.java"), StandardCharsets.UTF_8);
        assertThat(address).contains("toString(").contains("equals(").contains("hashCode(");
    }

    @Test
    @DisplayName("7: generateEqualsAndHashCode=false on the task -> Address.java has no equals/hashCode")
    void generate_equalsAndHashCodeDisabledOnTask_addressHasNoEqualsOrHashCode() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("product.json", PRODUCT_JSON);
        Path outputDir = tempDir.resolve("out");
        GenerateJsonPojosTask task = configuredTask(project, input, outputDir, REFERENCE_MAP_PATHS);
        task.getGenerateEqualsAndHashCode().set(false);

        task.generate();

        String address = Files.readString(outputDir.resolve("com/acme/model/Address.java"), StandardCharsets.UTF_8);
        assertThat(address).doesNotContain("equals(").doesNotContain("hashCode(");
    }

    // ---------------------------------------------------------------------------------------------
    // 8: errors
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("8: invalid JSON -> GradleException 'Invalid JSON in <absolute path>'")
    void generate_invalidJson_throwsGradleException() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("broken.json", "{\"a\":");
        GenerateJsonPojosTask task = configuredTask(project, input, tempDir.resolve("out"));

        assertThatThrownBy(task::generate)
                .isInstanceOf(GradleException.class)
                .hasMessageStartingWith("Invalid JSON in " + input.getAbsolutePath());
    }

    @Test
    @DisplayName("8: class name collision -> GradleException 'Code generation failed for <path>' with IAE cause")
    void generate_classNameCollision_throwsCodeGenerationFailed() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile(
                "collision.json", "{\"billing\":{\"address\":{\"a\":1}},\"shipping\":{\"address\":{\"b\":2}}}");
        GenerateJsonPojosTask task = configuredTask(project, input, tempDir.resolve("out"));

        assertThatThrownBy(task::generate)
                .isInstanceOf(GradleException.class)
                .hasMessageStartingWith("Code generation failed for " + input.getAbsolutePath())
                .hasMessageContaining("Class name Address used by both")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName(
            "8: invalid packageName 'com.9x' -> GradleException 'Code generation failed for' / 'Invalid package name'")
    void generate_invalidPackageName_throwsCodeGenerationFailed() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("product.json", PRODUCT_JSON);
        GenerateJsonPojosTask task = configuredTask(project, input, tempDir.resolve("out"), REFERENCE_MAP_PATHS);
        task.getPackageName().set("com.9x");

        assertThatThrownBy(task::generate)
                .isInstanceOf(GradleException.class)
                .hasMessageStartingWith("Code generation failed for " + input.getAbsolutePath())
                .hasMessageContaining("Invalid package name")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("8: unmatched map path -> GradleException 'Map path error for <absolute path>'")
    void generate_unmatchedMapPath_throwsMapPathError() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("input.json", "{\"a\":1}");
        GenerateJsonPojosTask task = configuredTask(project, input, tempDir.resolve("out"), "$.nope");

        assertThatThrownBy(task::generate)
                .isInstanceOf(GradleException.class)
                .hasMessageStartingWith("Map path error for " + input.getAbsolutePath() + ": ")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("8: type conflict -> GradleException 'Type conflict in <absolute path>'")
    void generate_typeConflict_throwsTypeConflict() throws IOException {
        Project project = newProjectWithPlugin();
        File input = writeFile("input.json", "{\"a\":[{\"x\":1},[1]]}");
        GenerateJsonPojosTask task = configuredTask(project, input, tempDir.resolve("out"));

        assertThatThrownBy(task::generate)
                .isInstanceOf(GradleException.class)
                .hasMessageStartingWith("Type conflict in " + input.getAbsolutePath() + ": ")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("8: a missing input file -> IOException")
    void generate_missingInputFile_throwsIOException() throws IOException {
        Project project = newProjectWithPlugin();
        File missing = tempDir.resolve("does-not-exist.json").toFile();
        GenerateJsonPojosTask task = configuredTask(project, missing, tempDir.resolve("out"));

        assertThatThrownBy(task::generate).isInstanceOf(IOException.class);
    }
}
