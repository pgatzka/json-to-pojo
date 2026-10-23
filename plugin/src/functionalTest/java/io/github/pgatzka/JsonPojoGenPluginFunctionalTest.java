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

    /** The 14 paths of the list-json-paths reference, each logged as {@code <path>: <type>} plus flag suffix. */
    private static final List<String> REFERENCE_LINES = List.of(
            "$.id: integer",
            "$.name: string",
            "$.address: object",
            "$.address.city: string",
            "$.address.zip: string",
            "$.tags: array",
            "$.tags[*]: string",
            "$.items: array",
            "$.items[*]: object",
            "$.items[*].sku: string",
            "$.items[*].qty: integer (optional)",
            "$.items[*].price: float (optional)",
            "$.empty: object",
            "$.none: null (nullable)");

    private static final String TYPED_REFERENCE_DOCUMENT = """
            {
              "id": 1,
              "name": "widget",
              "price": 9.5,
              "active": true,
              "address": {"city": "Berlin", "zip": "10115"},
              "tags": ["a", "b"],
              "items": [{"sku": "s1", "qty": 2, "note": null, "flag": 1}, {"sku": "s2", "qty": 2.5, "note": "x", "flag": true}],
              "empty": {},
              "none": null
            }
            """;

    private static final List<String> TYPED_REFERENCE_LINES = List.of(
            "$.id: integer",
            "$.name: string",
            "$.price: float",
            "$.active: boolean",
            "$.address: object",
            "$.address.city: string",
            "$.address.zip: string",
            "$.tags: array",
            "$.tags[*]: string",
            "$.items: array",
            "$.items[*]: object",
            "$.items[*].sku: string",
            "$.items[*].qty: float",
            "$.items[*].note: string (nullable)",
            "$.items[*].flag: string",
            "$.empty: object",
            "$.none: null (nullable)");

    /** Reference document of docs/specs/nullable-optional-paths.md (criterion 11). */
    private static final String FLAGGED_REFERENCE_DOCUMENT = """
            {
              "id": 1,
              "name": "widget",
              "price": 9.5,
              "active": true,
              "address": {"city": "Berlin", "zip": "10115"},
              "tags": ["a", "b"],
              "items": [
                {"sku": "s1", "qty": 2, "note": null, "flag": 1},
                {"sku": "s2", "qty": 2.5, "note": "x", "flag": true, "price": 1.5}
              ],
              "empty": {},
              "none": null
            }
            """;

    private static final List<String> FLAGGED_REFERENCE_LINES = List.of(
            "$.id: integer",
            "$.name: string",
            "$.price: float",
            "$.active: boolean",
            "$.address: object",
            "$.address.city: string",
            "$.address.zip: string",
            "$.tags: array",
            "$.tags[*]: string",
            "$.items: array",
            "$.items[*]: object",
            "$.items[*].sku: string",
            "$.items[*].qty: float",
            "$.items[*].note: string (nullable)",
            "$.items[*].flag: string",
            "$.items[*].price: float (optional)",
            "$.empty: object",
            "$.none: null (nullable)");

    private static final String CONFIGURED_BUILD_SCRIPT = "plugins {\n"
            + "    id(\"io.github.pgatzka.json-pojo-gen\")\n"
            + "}\n"
            + "jsonPojoGen {\n"
            + "    inputFile.set(file(\"input.json\"))\n"
            + "}\n";

    private static final String UNCONFIGURED_BUILD_SCRIPT =
            "plugins {\n" + "    id(\"io.github.pgatzka.json-pojo-gen\")\n" + "}\n";

    private static final String MAP_REFERENCE_DOCUMENT = """
            {
              "id": 7,
              "translations": {"en": {"text": "Hello", "plural": false}, "de": {"text": "Hallo"}},
              "items": [{"sku": "s1", "attrs": {"color": "red"}}, {"sku": "s2", "attrs": {"size": 3}}],
              "empty": {}
            }
            """;

    /** The 11 paths of the map-paths reference, each now logged as {@code <path>: <type>}. */
    private static final List<String> MAP_REFERENCE_LINES = List.of(
            "$.id: integer",
            "$.translations: object",
            "$.translations.*: object",
            "$.translations.*.text: string",
            "$.translations.*.plural: boolean (optional)",
            "$.items: array",
            "$.items[*]: object",
            "$.items[*].sku: string",
            "$.items[*].attrs: object",
            "$.items[*].attrs.*: string",
            "$.empty: object");

    /** Kotlin DSL: {@code \$} escapes the dollar sign inside a Kotlin string literal. */
    private static final String MAP_PATHS_BUILD_SCRIPT = "plugins {\n"
            + "    id(\"io.github.pgatzka.json-pojo-gen\")\n"
            + "}\n"
            + "jsonPojoGen {\n"
            + "    inputFile.set(file(\"input.json\"))\n"
            + "    mapPaths.add(\"\\$.translations\")\n"
            + "    mapPaths.add(\"\\$.items[*].attrs\")\n"
            + "}\n";

    private static final String ARRAY_MAP_PATH_BUILD_SCRIPT = "plugins {\n"
            + "    id(\"io.github.pgatzka.json-pojo-gen\")\n"
            + "}\n"
            + "jsonPojoGen {\n"
            + "    inputFile.set(file(\"input.json\"))\n"
            + "    mapPaths.add(\"\\$.items\")\n"
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
        return result.getOutput()
                .lines()
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
    @DisplayName("list-json-paths 10: listJsonPaths logs exactly the 14 typed paths, one per line, in order")
    void listJsonPaths_referenceDocument_logsExpectedPathsInOrder() throws IOException {
        writeProject(CONFIGURED_BUILD_SCRIPT, REFERENCE_DOCUMENT);

        BuildResult result = listJsonPathsRunner().build();

        List<String> strippedLines =
                result.getOutput().lines().map(String::strip).toList();
        assertThat(strippedLines).containsSubsequence(REFERENCE_LINES);
        assertThat(dollarLines(result)).containsExactlyElementsOf(REFERENCE_LINES);
    }

    @Test
    @DisplayName(
            "typed 9: listJsonPaths on the typed reference succeeds and logs the 17 '<path>: <type>' lines in order")
    void listJsonPaths_typedReferenceDocument_logsTypedLinesInOrder() throws IOException {
        writeProject(CONFIGURED_BUILD_SCRIPT, TYPED_REFERENCE_DOCUMENT);

        BuildResult result = listJsonPathsRunner().build();

        BuildTask task = result.task(":listJsonPaths");
        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        List<String> strippedLines =
                result.getOutput().lines().map(String::strip).toList();
        assertThat(strippedLines).containsSubsequence(TYPED_REFERENCE_LINES);
        assertThat(dollarLines(result)).containsExactlyElementsOf(TYPED_REFERENCE_LINES);
    }

    @Test
    @DisplayName("nullable 11: listJsonPaths on the flagged reference succeeds and logs the 18 flagged lines in order")
    void listJsonPaths_flaggedReferenceDocument_logsFlaggedLinesInOrder() throws IOException {
        writeProject(CONFIGURED_BUILD_SCRIPT, FLAGGED_REFERENCE_DOCUMENT);

        BuildResult result = listJsonPathsRunner().build();

        BuildTask task = result.task(":listJsonPaths");
        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        List<String> strippedLines =
                result.getOutput().lines().map(String::strip).toList();
        assertThat(strippedLines).containsSubsequence(FLAGGED_REFERENCE_LINES);
        assertThat(dollarLines(result)).containsExactlyElementsOf(FLAGGED_REFERENCE_LINES);
    }

    @Test
    @DisplayName("typed 10: listJsonPaths fails on a container type conflict")
    void listJsonPaths_typeConflict_fails() throws IOException {
        writeProject(CONFIGURED_BUILD_SCRIPT, "[{\"a\":{\"x\":1}},{\"a\":[1]}]");

        BuildResult result = listJsonPathsRunner().buildAndFail();

        BuildTask task = result.task(":listJsonPaths");
        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(TaskOutcome.FAILED);
        assertThat(result.getOutput())
                .contains("Type conflict in ")
                .contains("Conflicting types at $[*].a: object, array");
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

    @Test
    @DisplayName("map-paths 10: listJsonPaths with map paths succeeds and logs the 11 typed collapsed paths in order")
    void listJsonPaths_withMapPaths_logsCollapsedPathsInOrder() throws IOException {
        writeProject(MAP_PATHS_BUILD_SCRIPT, MAP_REFERENCE_DOCUMENT);

        BuildResult result = listJsonPathsRunner().build();

        BuildTask task = result.task(":listJsonPaths");
        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        List<String> strippedLines =
                result.getOutput().lines().map(String::strip).toList();
        assertThat(strippedLines).containsSubsequence(MAP_REFERENCE_LINES);
        assertThat(dollarLines(result)).containsExactlyElementsOf(MAP_REFERENCE_LINES);
    }

    @Test
    @DisplayName("map-paths 11: listJsonPaths fails when a map path points to an array")
    void listJsonPaths_mapPathToArray_fails() throws IOException {
        writeProject(ARRAY_MAP_PATH_BUILD_SCRIPT, MAP_REFERENCE_DOCUMENT);

        BuildResult result = listJsonPathsRunner().buildAndFail();

        BuildTask task = result.task(":listJsonPaths");
        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(TaskOutcome.FAILED);
        assertThat(result.getOutput()).contains("Map path error for ").contains("does not point to a JSON object");
    }

    // ---------------------------------------------------------------------------------------------
    // generate-pojos-task (docs/specs/generate-pojos-task.md)
    // ---------------------------------------------------------------------------------------------

    /** Reference input of docs/specs/pojo-generator.md ("Generation rules" -> "Reference input"). */
    private static final String PRODUCT_DOCUMENT = """
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

    /** The reference input with the {@code address} member removed. */
    private static final String PRODUCT_DOCUMENT_WITHOUT_ADDRESS = """
            {
              "id": 1,
              "name": "widget",
              "price": 9.5,
              "active": true,
              "first name": "x",
              "tags": ["a", "b"],
              "items": [
                {"sku": "s1", "qty": 2, "note": null, "attrs": {"color": "red"}},
                {"sku": "s2", "qty": 2.5, "note": "x", "price": 1.5, "attrs": {"size": 3}}
              ],
              "translations": {"en": {"text": "Hello", "plural": false}, "de": {"text": "Hallo"}},
              "none": null
            }
            """;

    private static final String JAVA_PLUGINS_BLOCK = """
            plugins {
                java
                id("io.github.pgatzka.json-pojo-gen")
            }
            repositories { mavenCentral() }
            dependencies { implementation("com.fasterxml.jackson.core:jackson-annotations:2.22") }
            """;

    /** Kotlin DSL: {@code \$} escapes the dollar sign inside a Kotlin string literal. */
    private static final String CODEGEN_EXTENSION_BLOCK = """
            jsonPojoGen {
                inputFile.set(file("product.json"))
                mapPaths.add("\\$.translations")
                mapPaths.add("\\$.items[*].attrs")
                packageName.set("com.acme.model")
            }
            """;

    private static final String CODEGEN_BUILD_SCRIPT = JAVA_PLUGINS_BLOCK + CODEGEN_EXTENSION_BLOCK;

    private static final String CODEGEN_WITHOUT_JAVA_BUILD_SCRIPT = """
            plugins {
                id("io.github.pgatzka.json-pojo-gen")
            }
            """ + CODEGEN_EXTENSION_BLOCK;

    private static final String CODEGEN_WITHOUT_PACKAGE_BUILD_SCRIPT = JAVA_PLUGINS_BLOCK + """
            jsonPojoGen {
                inputFile.set(file("product.json"))
                mapPaths.add("\\$.translations")
                mapPaths.add("\\$.items[*].attrs")
            }
            """;

    private static final String CODEGEN_FLAGS_OFF_BUILD_SCRIPT = JAVA_PLUGINS_BLOCK + """
            jsonPojoGen {
                inputFile.set(file("product.json"))
                mapPaths.add("\\$.translations")
                mapPaths.add("\\$.items[*].attrs")
                packageName.set("com.acme.model")
                generateEqualsAndHashCode.set(false)
                generateToString.set(false)
            }
            """;

    private static final List<String> GENERATED_CLASS_NAMES = List.of("Product", "Address", "Item", "Translation");

    private void writeCodegenProject(String buildScript, String productJson) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("build.gradle.kts"), buildScript, StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("product.json"), productJson, StandardCharsets.UTF_8);
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withArguments(arguments)
                .withProjectDir(projectDir.toFile());
    }

    private Path generatedPackageDir() {
        return projectDir.resolve("build/generated/sources/jsonPojoGen/java/main/com/acme/model");
    }

    private Path compiledPackageDir() {
        return projectDir.resolve("build/classes/java/main/com/acme/model");
    }

    private static void assertOutcome(BuildResult result, String taskPath, TaskOutcome expected) {
        BuildTask task = result.task(taskPath);
        assertThat(task).as(taskPath).isNotNull();
        assertThat(task.getOutcome()).as(taskPath).isEqualTo(expected);
    }

    @Test
    @DisplayName("generate-pojos 9: compileJava runs generateJsonPojos and compileJava with SUCCESS")
    void compileJava_referenceProject_bothTasksSucceed() throws IOException {
        writeCodegenProject(CODEGEN_BUILD_SCRIPT, PRODUCT_DOCUMENT);

        BuildResult result = runner("compileJava").build();

        assertOutcome(result, ":generateJsonPojos", TaskOutcome.SUCCESS);
        assertOutcome(result, ":compileJava", TaskOutcome.SUCCESS);
    }

    @Test
    @DisplayName("generate-pojos 9: compileJava generates the four sources and compiles the four classes")
    void compileJava_referenceProject_writesSourcesAndClasses() throws IOException {
        writeCodegenProject(CODEGEN_BUILD_SCRIPT, PRODUCT_DOCUMENT);

        runner("compileJava").build();

        for (String name : GENERATED_CLASS_NAMES) {
            assertThat(generatedPackageDir().resolve(name + ".java")).isRegularFile();
            assertThat(compiledPackageDir().resolve(name + ".class")).isRegularFile();
        }
    }

    @Test
    @DisplayName("generate-pojos API: generateJsonPojos logs 'Generated 4 Java file(s) into <dir>'")
    void generateJsonPojos_logsLifecycleLine() throws IOException {
        writeCodegenProject(CODEGEN_BUILD_SCRIPT, PRODUCT_DOCUMENT);

        BuildResult result = runner("generateJsonPojos").build();

        assertThat(result.getOutput()).contains("Generated 4 Java file(s) into ");
    }

    @Test
    @DisplayName("generate-pojos 10: a second compileJava without changes is UP-TO-DATE for generateJsonPojos")
    void compileJava_secondRunWithoutChanges_generateIsUpToDate() throws IOException {
        writeCodegenProject(CODEGEN_BUILD_SCRIPT, PRODUCT_DOCUMENT);
        runner("compileJava").build();

        BuildResult second = runner("compileJava").build();

        assertOutcome(second, ":generateJsonPojos", TaskOutcome.UP_TO_DATE);
    }

    @Test
    @DisplayName("generate-pojos 11: removing 'address' regenerates and removes Address.java and Address.class")
    void compileJava_afterRemovingAddress_removesStaleSourceAndClass() throws IOException {
        writeCodegenProject(CODEGEN_BUILD_SCRIPT, PRODUCT_DOCUMENT);
        runner("compileJava").build();
        assertThat(generatedPackageDir().resolve("Address.java")).isRegularFile();
        assertThat(compiledPackageDir().resolve("Address.class")).isRegularFile();

        Files.writeString(projectDir.resolve("product.json"), PRODUCT_DOCUMENT_WITHOUT_ADDRESS, StandardCharsets.UTF_8);
        BuildResult second = runner("compileJava").build();

        assertOutcome(second, ":generateJsonPojos", TaskOutcome.SUCCESS);
        assertThat(generatedPackageDir().resolve("Address.java")).doesNotExist();
        assertThat(compiledPackageDir().resolve("Address.class")).doesNotExist();
        assertThat(generatedPackageDir().resolve("Product.java")).isRegularFile();
    }

    @Test
    @DisplayName("generate-pojos 12: without the java plugin generateJsonPojos succeeds and writes the four files")
    void generateJsonPojos_withoutJavaPlugin_succeedsAndWritesFiles() throws IOException {
        writeCodegenProject(CODEGEN_WITHOUT_JAVA_BUILD_SCRIPT, PRODUCT_DOCUMENT);

        BuildResult result = runner("generateJsonPojos").build();

        assertOutcome(result, ":generateJsonPojos", TaskOutcome.SUCCESS);
        for (String name : GENERATED_CLASS_NAMES) {
            assertThat(generatedPackageDir().resolve(name + ".java")).isRegularFile();
        }
    }

    @Test
    @DisplayName("generate-pojos 13: without packageName generateJsonPojos fails mentioning property 'packageName'")
    void generateJsonPojos_withoutPackageName_fails() throws IOException {
        writeCodegenProject(CODEGEN_WITHOUT_PACKAGE_BUILD_SCRIPT, PRODUCT_DOCUMENT);

        BuildResult result = runner("generateJsonPojos").buildAndFail();

        assertThat(result.getOutput()).contains("property 'packageName'");
    }

    @Test
    @DisplayName("generate-pojos 14: with both flags off Address.java has no equals(, hashCode( or toString(")
    void generateJsonPojos_flagsOff_addressHasNoEqualsHashCodeToString() throws IOException {
        writeCodegenProject(CODEGEN_FLAGS_OFF_BUILD_SCRIPT, PRODUCT_DOCUMENT);

        BuildResult result = runner("generateJsonPojos").build();

        assertOutcome(result, ":generateJsonPojos", TaskOutcome.SUCCESS);
        String address = Files.readString(generatedPackageDir().resolve("Address.java"), StandardCharsets.UTF_8);
        assertThat(address)
                .doesNotContain("equals(")
                .doesNotContain("hashCode(")
                .doesNotContain("toString(");
    }
}
