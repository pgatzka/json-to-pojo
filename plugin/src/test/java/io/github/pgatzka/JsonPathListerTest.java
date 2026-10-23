package io.github.pgatzka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private static final String MAP_REFERENCE_DOCUMENT = """
            {
              "id": 7,
              "translations": {"en": {"text": "Hello", "plural": false}, "de": {"text": "Hallo"}},
              "items": [{"sku": "s1", "attrs": {"color": "red"}}, {"sku": "s2", "attrs": {"size": 3}}],
              "empty": {}
            }
            """;

    private static final Set<String> MAP_REFERENCE_MAP_PATHS = Set.of("$.translations", "$.items[*].attrs");

    private static final List<String> MAP_REFERENCE_PATHS = List.of(
            "$.id",
            "$.translations",
            "$.translations.*",
            "$.translations.*.text",
            "$.translations.*.plural",
            "$.items",
            "$.items[*]",
            "$.items[*].sku",
            "$.items[*].attrs",
            "$.items[*].attrs.*",
            "$.empty");

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

    // ---------------------------------------------------------------------------------------------
    // map-paths (docs/specs/map-paths.md)
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("map-paths 1: reference document with map paths yields the 11 expected paths in order")
    void listPathsWithMapPaths_referenceDocument_returnsExpectedPathsInOrder() {
        List<String> paths = JsonPathLister.listPaths(MAP_REFERENCE_DOCUMENT, MAP_REFERENCE_MAP_PATHS);

        assertThat(paths).containsExactlyElementsOf(MAP_REFERENCE_PATHS);
        assertThat(paths).hasSize(11);
    }

    @Test
    @DisplayName("map-paths 1: map member names never appear in the output")
    void listPathsWithMapPaths_referenceDocument_omitsMemberNames() {
        List<String> paths = JsonPathLister.listPaths(MAP_REFERENCE_DOCUMENT, MAP_REFERENCE_MAP_PATHS);

        assertThat(paths).noneMatch(path -> path.contains(".en") || path.contains(".de")
                || path.contains(".color") || path.contains(".size"));
    }

    @Test
    @DisplayName("map-paths 2: empty map paths behave like the single-argument overload")
    void listPathsWithEmptyMapPaths_equalsSingleArgumentOverload() {
        List<String> withEmptySet = JsonPathLister.listPaths(MAP_REFERENCE_DOCUMENT, Set.of());
        List<String> withoutSet = JsonPathLister.listPaths(MAP_REFERENCE_DOCUMENT);

        assertThat(withEmptySet).isEqualTo(withoutSet);
        assertThat(withEmptySet).contains("$.translations.en", "$.translations.de.text", "$.items[*].attrs.color");
    }

    @Test
    @DisplayName("map-paths 3: root object as map collapses to $.*")
    void listPathsWithMapPaths_rootAsMap_collapsesToRootWildcard() {
        List<String> paths = JsonPathLister.listPaths("{\"a\":{\"x\":1},\"b\":{\"y\":2}}", Set.of("$"));

        assertThat(paths).containsExactly("$.*", "$.*.x", "$.*.y");
    }

    @Test
    @DisplayName("map-paths 4: map inside the values of another map")
    void listPathsWithMapPaths_mapInsideMap_collapsesBoth() {
        List<String> paths = JsonPathLister.listPaths(
                "{\"i18n\":{\"en\":{\"meta\":{\"k\":1}},\"de\":{\"meta\":{\"j\":2}}}}",
                Set.of("$.i18n", "$.i18n.*.meta"));

        assertThat(paths).containsExactly("$.i18n", "$.i18n.*", "$.i18n.*.meta", "$.i18n.*.meta.*");
    }

    @Test
    @DisplayName("map-paths 5: empty object as map lists only its own path")
    void listPathsWithMapPaths_emptyObjectAsMap_listsOnlyItsPath() {
        List<String> paths = JsonPathLister.listPaths("{\"m\":{}}", Set.of("$.m"));

        assertThat(paths).containsExactly("$.m");
    }

    @Test
    @DisplayName("map-paths 6: null value at a map path is tolerated")
    void listPathsWithMapPaths_nullNextToObject_isTolerated() {
        List<String> paths = JsonPathLister.listPaths(
                "{\"items\":[{\"attrs\":{\"a\":1}},{\"attrs\":null}]}",
                Set.of("$.items[*].attrs"));

        assertThat(paths).containsExactly("$.items", "$.items[*]", "$.items[*].attrs", "$.items[*].attrs.*");
    }

    @Test
    @DisplayName("map-paths 6/rules: a map path matching only null counts as found and is listed as a scalar")
    void listPathsWithMapPaths_onlyNullMatched_listsScalarWithoutError() {
        List<String> paths = JsonPathLister.listPaths("{\"a\":null}", Set.of("$.a"));

        assertThat(paths).containsExactly("$.a");
    }

    @Test
    @DisplayName("map-paths 7: bracket-notation map path matches")
    void listPathsWithMapPaths_bracketNotationPath_collapses() {
        List<String> paths = JsonPathLister.listPaths("{\"first name\":{\"a\":1}}", Set.of("$['first name']"));

        assertThat(paths).containsExactly("$['first name']", "$['first name'].*");
    }

    @Test
    @DisplayName("map-paths 8: reading a UTF-8 file with map paths")
    void listPathsFromFileWithMapPaths_utf8File_returnsCollapsedPaths() throws IOException {
        Path file = tempDir.resolve("map.json");
        Files.writeString(file, "{\"m\":{\"a\":1}}", StandardCharsets.UTF_8);

        List<String> paths = JsonPathLister.listPaths(file, Set.of("$.m"));

        assertThat(paths).containsExactly("$.m", "$.m.*");
    }

    @Test
    @DisplayName("map-paths 8: reading the reference document from a file with map paths")
    void listPathsFromFileWithMapPaths_referenceDocument_returnsExpectedPaths() throws IOException {
        Path file = tempDir.resolve("map-reference.json");
        Files.writeString(file, MAP_REFERENCE_DOCUMENT, StandardCharsets.UTF_8);

        assertThat(JsonPathLister.listPaths(file, MAP_REFERENCE_MAP_PATHS))
                .containsExactlyElementsOf(MAP_REFERENCE_PATHS);
    }

    @Test
    @DisplayName("map-paths: first occurrence wins when merging map values")
    void listPathsWithMapPaths_mergedValues_firstOccurrenceWins() {
        List<String> paths = JsonPathLister.listPaths(
                "{\"m\":{\"k1\":{\"b\":1,\"a\":2},\"k2\":{\"a\":3,\"c\":4}}}",
                Set.of("$.m"));

        assertThat(paths).containsExactly("$.m", "$.m.*", "$.m.*.b", "$.m.*.a", "$.m.*.c");
    }

    @Test
    @DisplayName("map-paths: returned list is unmodifiable")
    void listPathsWithMapPaths_returnedList_isUnmodifiable() {
        List<String> paths = JsonPathLister.listPaths("{\"m\":{\"a\":1}}", Set.of("$.m"));

        assertThatThrownBy(() -> paths.add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("map-paths Edge: null map paths with a string throws NullPointerException")
    void listPathsWithMapPaths_nullMapPaths_throwsNullPointerException() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{}", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map-paths Edge: null map paths with a file throws NullPointerException")
    void listPathsFromFileWithMapPaths_nullMapPaths_throwsNullPointerException() throws IOException {
        Path file = tempDir.resolve("npe.json");
        Files.writeString(file, "{}", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> JsonPathLister.listPaths(file, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map-paths Edge: null json with map paths throws NullPointerException")
    void listPathsWithMapPaths_nullJson_throwsNullPointerException() {
        assertThatThrownBy(() -> JsonPathLister.listPaths((String) null, Set.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map-paths Edge: null file with map paths throws NullPointerException")
    void listPathsFromFileWithMapPaths_nullFile_throwsNullPointerException() {
        assertThatThrownBy(() -> JsonPathLister.listPaths((Path) null, Set.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map-paths Edge: map paths containing null throws NullPointerException")
    void listPathsWithMapPaths_setContainingNull_throwsNullPointerException() {
        Set<String> withNull = new HashSet<>(Arrays.asList((String) null));

        assertThatThrownBy(() -> JsonPathLister.listPaths("{}", withNull))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map-paths Edge: empty map path is invalid")
    void listPathsWithMapPaths_emptyMapPath_throwsInvalidMapPath() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"a\":{}}", Set.of("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid map path");
    }

    @Test
    @DisplayName("map-paths Edge: blank map path is invalid")
    void listPathsWithMapPaths_blankMapPath_throwsInvalidMapPath() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"a\":{}}", Set.of("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid map path");
    }

    @Test
    @DisplayName("map-paths Edge: map path without leading $ is invalid")
    void listPathsWithMapPaths_mapPathWithoutDollar_throwsInvalidMapPath() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"items\":{\"a\":1}}", Set.of("items")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid map path");
    }

    @Test
    @DisplayName("map-paths Edge: map path syntax is validated before the JSON is parsed")
    void listPathsWithMapPaths_invalidMapPathAndInvalidJson_reportsInvalidMapPath() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"a\":", Set.of("items")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid map path");
    }

    @Test
    @DisplayName("map-paths Edge: file with invalid map path throws IllegalArgumentException")
    void listPathsFromFileWithMapPaths_invalidMapPath_throwsInvalidMapPath() throws IOException {
        Path file = tempDir.resolve("invalid-map-path.json");
        Files.writeString(file, "{\"items\":{\"a\":1}}", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> JsonPathLister.listPaths(file, Set.of("items")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid map path");
    }

    @Test
    @DisplayName("map-paths Edge: unmatched map path is reported as not found")
    void listPathsWithMapPaths_unmatchedMapPath_throwsNotFound() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"a\":1}", Set.of("$.nope")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Map path $.nope not found");
    }

    @Test
    @DisplayName("map-paths Edge: one of two map paths unmatched is reported as not found")
    void listPathsWithMapPaths_oneOfTwoUnmatched_throwsNotFoundForUnmatched() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"a\":{\"x\":1}}", Set.of("$.a", "$.b")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Map path $.b not found");
    }

    @Test
    @DisplayName("map-paths Edge: map path matched only by a member name of a map is not found")
    void listPathsWithMapPaths_pathThroughCollapsedMap_throwsNotFound() {
        assertThatThrownBy(() -> JsonPathLister.listPaths(
                "{\"m\":{\"k\":{\"x\":1}}}", Set.of("$.m", "$.m.k")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Map path $.m.k not found");
    }

    @Test
    @DisplayName("map-paths Edge: file with unmatched map path throws not found")
    void listPathsFromFileWithMapPaths_unmatchedMapPath_throwsNotFound() throws IOException {
        Path file = tempDir.resolve("not-found.json");
        Files.writeString(file, "{\"a\":1}", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> JsonPathLister.listPaths(file, Set.of("$.nope")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Map path $.nope not found");
    }

    @Test
    @DisplayName("map-paths Edge: map path to an array is not an object")
    void listPathsWithMapPaths_mapPathToArray_throwsNotAnObject() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"a\":[1]}", Set.of("$.a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Map path $.a does not point to a JSON object");
    }

    @Test
    @DisplayName("map-paths Edge: map path to a number is not an object")
    void listPathsWithMapPaths_mapPathToNumber_throwsNotAnObject() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"a\":1}", Set.of("$.a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Map path $.a does not point to a JSON object");
    }

    @Test
    @DisplayName("map-paths Edge: map path to a string is not an object")
    void listPathsWithMapPaths_mapPathToString_throwsNotAnObject() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"a\":\"x\"}", Set.of("$.a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Map path $.a does not point to a JSON object");
    }

    @Test
    @DisplayName("map-paths Edge: map path to a boolean is not an object")
    void listPathsWithMapPaths_mapPathToBoolean_throwsNotAnObject() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"a\":true}", Set.of("$.a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Map path $.a does not point to a JSON object");
    }

    @Test
    @DisplayName("map-paths Edge: root map path on an array root is not an object")
    void listPathsWithMapPaths_rootMapPathOnArrayRoot_throwsNotAnObject() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("[1]", Set.of("$")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Map path $ does not point to a JSON object");
    }

    @Test
    @DisplayName("map-paths Edge: invalid JSON is reported before map path errors")
    void listPathsWithMapPaths_invalidJson_reportsInvalidJson() {
        assertThatThrownBy(() -> JsonPathLister.listPaths("{\"a\":", Set.of("$.a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid JSON: ");
    }

    @Test
    @DisplayName("map-paths Edge: non-existent file with valid map paths throws IOException")
    void listPathsFromFileWithMapPaths_missingFile_throwsIOException() {
        Path missing = tempDir.resolve("does-not-exist.json");

        assertThatThrownBy(() -> JsonPathLister.listPaths(missing, Set.of("$.m")))
                .isInstanceOf(IOException.class);
    }
}
