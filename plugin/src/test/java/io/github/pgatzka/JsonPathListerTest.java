package io.github.pgatzka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link JsonPathLister}.
 */
class JsonPathListerTest {

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

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("1: reference document yields the 14 expected paths in order")
    void listPaths_referenceDocument_returnsExpectedPathsInOrder() {
        List<String> paths = JsonPathLister.listPaths(REFERENCE_DOCUMENT);

        assertThat(paths).containsExactlyElementsOf(REFERENCE_PATHS);
        assertThat(paths).hasSize(14);
    }

    @Test
    @DisplayName("2: nested objects are listed in pre-order")
    void listPaths_nestedObjects_returnsPreOrderPaths() {
        List<String> paths = JsonPathLister.listPaths("{\"a\":{\"b\":{\"c\":true}}}");

        assertThat(paths).containsExactly("$.a", "$.a.b", "$.a.b.c");
    }

    @Test
    @DisplayName("3: root array collapses elements to [*] and merges member paths")
    void listPaths_rootArray_collapsesAndMergesElements() {
        List<String> paths = JsonPathLister.listPaths("[{\"a\":1},{\"b\":2}]");

        assertThat(paths).containsExactly("$[*]", "$[*].a", "$[*].b");
    }

    @Test
    @DisplayName("3/Edge: merged array element paths appear once")
    void listPaths_arrayElementsWithSameMembers_listsEachPathOnce() {
        List<String> paths = JsonPathLister.listPaths("[{\"a\":1},{\"a\":2},{\"a\":3}]");

        assertThat(paths).containsExactly("$[*]", "$[*].a");
    }

    @Test
    @DisplayName("4: nested arrays collapse to [*][*]")
    void listPaths_nestedArrays_returnsWildcardPaths() {
        List<String> paths = JsonPathLister.listPaths("[[1,2],[3]]");

        assertThat(paths).containsExactly("$[*]", "$[*][*]");
    }

    @Test
    @DisplayName("5: non-identifier keys use escaped bracket notation")
    void listPaths_nonIdentifierKeys_useBracketNotation() {
        List<String> paths = JsonPathLister.listPaths(
                "{\"first name\":1,\"it's\":2,\"a\\\\b\":3,\"\":4,\"_ok9\":5}");

        assertThat(paths).containsExactly(
                "$['first name']",
                "$['it\\'s']",
                "$['a\\\\b']",
                "$['']",
                "$._ok9");
    }

    @Test
    @DisplayName("5: key starting with a digit uses bracket notation")
    void listPaths_keyStartingWithDigit_usesBracketNotation() {
        List<String> paths = JsonPathLister.listPaths("{\"9a\":1,\"A_b9\":2}");

        assertThat(paths).containsExactly("$['9a']", "$.A_b9");
    }

    @Test
    @DisplayName("6: number root yields an empty list")
    void listPaths_numberRoot_returnsEmptyList() {
        assertThat(JsonPathLister.listPaths("42")).isEmpty();
    }

    @Test
    @DisplayName("6: string root yields an empty list")
    void listPaths_stringRoot_returnsEmptyList() {
        assertThat(JsonPathLister.listPaths("\"x\"")).isEmpty();
    }

    @Test
    @DisplayName("6: null root yields an empty list")
    void listPaths_nullRoot_returnsEmptyList() {
        assertThat(JsonPathLister.listPaths("null")).isEmpty();
    }

    @Test
    @DisplayName("6: empty object root yields an empty list")
    void listPaths_emptyObjectRoot_returnsEmptyList() {
        assertThat(JsonPathLister.listPaths("{}")).isEmpty();
    }

    @Test
    @DisplayName("6: empty array root yields an empty list")
    void listPaths_emptyArrayRoot_returnsEmptyList() {
        assertThat(JsonPathLister.listPaths("[]")).isEmpty();
    }

    @Test
    @DisplayName("7: returned list is unmodifiable")
    void listPaths_returnedList_isUnmodifiable() {
        List<String> paths = JsonPathLister.listPaths("{\"a\":1}");

        assertThatThrownBy(() -> paths.add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("7: returned empty list is unmodifiable")
    void listPaths_returnedEmptyList_isUnmodifiable() {
        List<String> paths = JsonPathLister.listPaths("42");

        assertThatThrownBy(() -> paths.add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("8: reading a UTF-8 file lists its paths, non-ASCII key in brackets")
    void listPathsFromFile_utf8File_returnsPaths() throws IOException {
        Path file = tempDir.resolve("input.json");
        Files.writeString(file, "{\"ä\":1,\"b\":[true]}", StandardCharsets.UTF_8);

        List<String> paths = JsonPathLister.listPaths(file);

        assertThat(paths).containsExactly("$['ä']", "$.b", "$.b[*]");
    }

    @Test
    @DisplayName("8: reading the reference document from a file yields the same paths")
    void listPathsFromFile_referenceDocument_returnsExpectedPaths() throws IOException {
        Path file = tempDir.resolve("reference.json");
        Files.writeString(file, REFERENCE_DOCUMENT, StandardCharsets.UTF_8);

        assertThat(JsonPathLister.listPaths(file)).containsExactlyElementsOf(REFERENCE_PATHS);
    }

    @Test
    @DisplayName("Edge: JsonPathLister has no public constructor")
    void jsonPathLister_hasNoPublicConstructor() {
        assertThat(JsonPathLister.class.getConstructors()).isEmpty();
    }

    @Test
    @DisplayName("Edge: null string throws NullPointerException")
    void listPaths_nullString_throwsNullPointerException() {
        assertThatThrownBy(() -> JsonPathLister.listPaths((String) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: null path throws NullPointerException")
    void listPaths_nullPath_throwsNullPointerException() {
        assertThatThrownBy(() -> JsonPathLister.listPaths((Path) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: empty string throws IllegalArgumentException mentioning 'empty'")
    void listPaths_emptyString_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> JsonPathLister.listPaths(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Edge: blank string throws IllegalArgumentException mentioning 'empty'")
    void listPaths_blankString_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("   \n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Edge: truncated JSON throws IllegalArgumentException with Jackson cause")
    void listPaths_truncatedJson_throwsInvalidJson() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"a\":"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid JSON: ")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("Edge: non-JSON text throws IllegalArgumentException with Jackson cause")
    void listPaths_notJson_throwsInvalidJson() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid JSON: ")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("Edge: file with invalid JSON throws IllegalArgumentException")
    void listPathsFromFile_invalidJson_throwsInvalidJson() throws IOException {
        Path file = tempDir.resolve("broken.json");
        Files.writeString(file, "{\"a\":", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> JsonPathLister.listPaths(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid JSON: ")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("Edge: blank file throws IllegalArgumentException mentioning 'empty'")
    void listPathsFromFile_blankFile_throwsIllegalArgumentException() throws IOException {
        Path file = tempDir.resolve("blank.json");
        Files.writeString(file, "   \n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> JsonPathLister.listPaths(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Edge: non-existent file throws IOException")
    void listPathsFromFile_missingFile_throwsIOException() {
        Path missing = tempDir.resolve("does-not-exist.json");

        assertThatThrownBy(() -> JsonPathLister.listPaths(missing))
                .isInstanceOf(IOException.class);
    }
}
