package io.github.pgatzka.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GeneratorOptions}.
 */
class GeneratorOptionsTest {

    // ---------------------------------------------------------------------------------------------
    // Criterion 17: defaults and unmodifiable collections
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("17: defaults has empty mapPaths and classNames and both flags true")
    void defaults_hasEmptyCollectionsAndBothFlagsTrue() {
        GeneratorOptions options = GeneratorOptions.defaults("com.acme", "Root");

        assertThat(options.packageName()).isEqualTo("com.acme");
        assertThat(options.rootClassName()).isEqualTo("Root");
        assertThat(options.mapPaths()).isEmpty();
        assertThat(options.classNames()).isEmpty();
        assertThat(options.generateEqualsAndHashCode()).isTrue();
        assertThat(options.generateToString()).isTrue();
    }

    @Test
    @DisplayName("17: defaults equals the canonical constructor with empty collections and both flags true")
    void defaults_equalsCanonicalConstructor() {
        assertThat(GeneratorOptions.defaults("com.acme", "Root"))
                .isEqualTo(new GeneratorOptions("com.acme", "Root", Set.of(), Map.of(), true, true));
    }

    @Test
    @DisplayName("17: mapPaths of defaults is unmodifiable")
    void defaults_mapPaths_isUnmodifiable() {
        GeneratorOptions options = GeneratorOptions.defaults("com.acme", "Root");

        assertThatThrownBy(() -> options.mapPaths().add("$.m")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("17: classNames of defaults is unmodifiable")
    void defaults_classNames_isUnmodifiable() {
        GeneratorOptions options = GeneratorOptions.defaults("com.acme", "Root");

        assertThatThrownBy(() -> options.classNames().put("$.a", "A"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("17: mapPaths and classNames built from mutable collections are unmodifiable")
    void constructor_mutableCollections_areExposedUnmodifiable() {
        Set<String> mapPaths = new HashSet<>(Set.of("$.translations"));
        Map<String, String> classNames = new HashMap<>(Map.of("$.items[*]", "LineItem"));

        GeneratorOptions options = new GeneratorOptions("com.acme", "Root", mapPaths, classNames, true, false);

        assertThatThrownBy(() -> options.mapPaths().add("$.other")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> options.mapPaths().remove("$.translations"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> options.classNames().put("$.x", "X"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> options.classNames().remove("$.items[*]"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("17: mapPaths and classNames are copied, later changes to the inputs do not leak in")
    void constructor_copiesCollections() {
        Set<String> mapPaths = new HashSet<>(Set.of("$.translations"));
        Map<String, String> classNames = new HashMap<>(Map.of("$.items[*]", "LineItem"));

        GeneratorOptions options = new GeneratorOptions("com.acme", "Root", mapPaths, classNames, true, true);
        mapPaths.add("$.other");
        classNames.put("$.x", "X");

        assertThat(options.mapPaths()).containsOnly("$.translations");
        assertThat(options.classNames()).containsOnly(Map.entry("$.items[*]", "LineItem"));
    }

    @Test
    @DisplayName("Accessors return the constructor arguments")
    void accessors_returnComponents() {
        GeneratorOptions options = new GeneratorOptions(
                "com.acme.model", "Product", Set.of("$.translations"), Map.of("$", "Top"), false, true);

        assertThat(options.packageName()).isEqualTo("com.acme.model");
        assertThat(options.rootClassName()).isEqualTo("Product");
        assertThat(options.mapPaths()).containsOnly("$.translations");
        assertThat(options.classNames()).containsOnly(Map.entry("$", "Top"));
        assertThat(options.generateEqualsAndHashCode()).isFalse();
        assertThat(options.generateToString()).isTrue();
    }

    @Test
    @DisplayName("Valid single-segment package and underscore class name are accepted")
    void defaults_validNames_areAccepted() {
        GeneratorOptions options = GeneratorOptions.defaults("model", "_Root9");

        assertThat(options.packageName()).isEqualTo("model");
        assertThat(options.rootClassName()).isEqualTo("_Root9");
    }

    // ---------------------------------------------------------------------------------------------
    // Edge cases: null arguments
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Edge: null mapPaths throws NullPointerException")
    void constructor_nullMapPaths_throwsNullPointerException() {
        assertThatThrownBy(() -> new GeneratorOptions("com.acme", "Root", null, Map.of(), true, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: null classNames throws NullPointerException")
    void constructor_nullClassNames_throwsNullPointerException() {
        assertThatThrownBy(() -> new GeneratorOptions("com.acme", "Root", Set.of(), null, true, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: null packageName throws NullPointerException")
    void constructor_nullPackageName_throwsNullPointerException() {
        assertThatThrownBy(() -> new GeneratorOptions(null, "Root", Set.of(), Map.of(), true, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: null rootClassName throws NullPointerException")
    void constructor_nullRootClassName_throwsNullPointerException() {
        assertThatThrownBy(() -> new GeneratorOptions("com.acme", null, Set.of(), Map.of(), true, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: defaults with null packageName throws NullPointerException")
    void defaults_nullPackageName_throwsNullPointerException() {
        assertThatThrownBy(() -> GeneratorOptions.defaults(null, "Root")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: defaults with null rootClassName throws NullPointerException")
    void defaults_nullRootClassName_throwsNullPointerException() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com.acme", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: null element in mapPaths throws NullPointerException")
    void constructor_nullMapPathElement_throwsNullPointerException() {
        Set<String> mapPaths = new HashSet<>(Arrays.asList("$.m", null));

        assertThatThrownBy(() -> new GeneratorOptions("com.acme", "Root", mapPaths, Map.of(), true, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: null key in classNames throws NullPointerException")
    void constructor_nullClassNamesKey_throwsNullPointerException() {
        Map<String, String> classNames = new HashMap<>();
        classNames.put(null, "X");

        assertThatThrownBy(() -> new GeneratorOptions("com.acme", "Root", Set.of(), classNames, true, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: null value in classNames throws NullPointerException")
    void constructor_nullClassNamesValue_throwsNullPointerException() {
        Map<String, String> classNames = new HashMap<>();
        classNames.put("$.a", null);

        assertThatThrownBy(() -> new GeneratorOptions("com.acme", "Root", Set.of(), classNames, true, true))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------------------------------------
    // Edge cases: invalid package names
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Edge: package segment starting with a digit is invalid")
    void defaults_packageSegmentStartingWithDigit_throwsInvalidPackageName() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com.9acme", "Root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid package name");
    }

    @Test
    @DisplayName("Edge: package segment that is a keyword is invalid")
    void defaults_packageSegmentKeyword_throwsInvalidPackageName() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com.class", "Root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid package name");
    }

    @Test
    @DisplayName("Edge: empty package name is invalid")
    void defaults_emptyPackageName_throwsInvalidPackageName() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("", "Root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid package name");
    }

    @Test
    @DisplayName("Edge: package name with an empty segment is invalid")
    void defaults_packageNameWithEmptySegment_throwsInvalidPackageName() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com..acme", "Root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid package name");
    }

    @Test
    @DisplayName("Edge: package name with a trailing dot is invalid")
    void defaults_packageNameWithTrailingDot_throwsInvalidPackageName() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com.acme.", "Root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid package name");
    }

    @Test
    @DisplayName("Edge: package name with a non-identifier character is invalid")
    void defaults_packageNameWithDash_throwsInvalidPackageName() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com.my-acme", "Root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid package name");
    }

    // ---------------------------------------------------------------------------------------------
    // Edge cases: invalid class names
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Edge: root class name starting with a digit is invalid")
    void defaults_rootClassNameStartingWithDigit_throwsInvalidClassName() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com.acme", "9Root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid class name");
    }

    @Test
    @DisplayName("Edge: root class name that is a keyword is invalid")
    void defaults_rootClassNameKeyword_throwsInvalidClassName() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com.acme", "class"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid class name");
    }

    @Test
    @DisplayName("Edge: empty root class name is invalid")
    void defaults_emptyRootClassName_throwsInvalidClassName() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com.acme", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid class name");
    }

    @Test
    @DisplayName("Edge: classNames value that is not an identifier is invalid")
    void constructor_classNamesValueWithSpace_throwsInvalidClassName() {
        assertThatThrownBy(() -> new GeneratorOptions(
                        "com.acme", "Root", Set.of(), Map.of("$.items[*]", "my class"), true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid class name");
    }

    @Test
    @DisplayName("Edge: classNames value that is a keyword is invalid")
    void constructor_classNamesValueKeyword_throwsInvalidClassName() {
        assertThatThrownBy(() ->
                        new GeneratorOptions("com.acme", "Root", Set.of(), Map.of("$.items[*]", "int"), true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid class name");
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 19: reserved class names and restricted identifiers
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("19: root class name Object is reserved")
    void defaults_rootClassNameObject_throwsReserved() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com.acme", "Object"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Class name Object is reserved");
    }

    @Test
    @DisplayName("19: root class name record is an invalid class name")
    void defaults_rootClassNameRecord_throwsInvalidClassName() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com.acme", "record"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid class name");
    }

    @Test
    @DisplayName("19: root class name var is an invalid class name")
    void defaults_rootClassNameVar_throwsInvalidClassName() {
        assertThatThrownBy(() -> GeneratorOptions.defaults("com.acme", "var"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid class name");
    }

    @Test
    @DisplayName("19: restricted identifiers var, yield, record, sealed, permits are invalid root class names")
    void defaults_restrictedIdentifierRootClassNames_throwInvalidClassName() {
        for (String name : List.of("var", "yield", "record", "sealed", "permits")) {
            assertThatThrownBy(() -> GeneratorOptions.defaults("com.acme", name))
                    .as(name)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Invalid class name");
        }
    }

    @Test
    @DisplayName("19: restricted identifiers var, yield, record, sealed, permits are invalid classNames values")
    void constructor_restrictedIdentifierClassNamesValues_throwInvalidClassName() {
        for (String name : List.of("var", "yield", "record", "sealed", "permits")) {
            assertThatThrownBy(() ->
                            new GeneratorOptions("com.acme", "Root", Set.of(), Map.of("$.items[*]", name), true, true))
                    .as(name)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Invalid class name");
        }
    }

    @Test
    @DisplayName("19: java.lang type names and generated-code imports are reserved root class names")
    void defaults_reservedRootClassNames_throwReserved() {
        for (String name : List.of(
                "Object",
                "String",
                "Long",
                "Double",
                "Boolean",
                "Record",
                "Thread",
                "Override",
                "List",
                "Map",
                "Objects",
                "JsonProperty",
                "Generated")) {
            assertThatThrownBy(() -> GeneratorOptions.defaults("com.acme", name))
                    .as(name)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Class name " + name + " is reserved");
        }
    }

    @Test
    @DisplayName("19: reserved names are rejected as classNames values")
    void constructor_reservedClassNamesValues_throwReserved() {
        for (String name :
                List.of("Object", "String", "Thread", "List", "Map", "Objects", "JsonProperty", "Generated")) {
            assertThatThrownBy(() -> new GeneratorOptions(
                            "com.acme", "Root", Set.of(), Map.of("$.objects[*]", name), true, true))
                    .as(name)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Class name " + name + " is reserved");
        }
    }

    @Test
    @DisplayName("19: non-reserved class names are accepted as root and classNames values")
    void constructor_nonReservedClassNames_areAccepted() {
        GeneratorOptions options =
                new GeneratorOptions("com.acme", "Product", Set.of(), Map.of("$.objects[*]", "Thing"), true, true);

        assertThat(options.rootClassName()).isEqualTo("Product");
        assertThat(options.classNames()).containsOnly(Map.entry("$.objects[*]", "Thing"));
    }

    // ---------------------------------------------------------------------------------------------
    // Edge cases: invalid paths
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Edge: classNames key without leading $ is invalid")
    void constructor_classNamesKeyWithoutDollar_throwsInvalidPath() {
        assertThatThrownBy(
                        () -> new GeneratorOptions("com.acme", "Root", Set.of(), Map.of("items", "Item"), true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid path");
    }

    @Test
    @DisplayName("Edge: mapPaths element without leading $ is invalid")
    void constructor_mapPathWithoutDollar_throwsInvalidPath() {
        assertThatThrownBy(() -> new GeneratorOptions("com.acme", "Root", Set.of("items"), Map.of(), true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid path");
    }
}
