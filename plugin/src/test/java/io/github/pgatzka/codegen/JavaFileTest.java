package io.github.pgatzka.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JavaFile}.
 */
class JavaFileTest {

    @Test
    @DisplayName("Accessors return the constructor arguments")
    void accessors_returnComponents() {
        JavaFile file = new JavaFile("com.acme.model", "Product", "class Product {}\n");

        assertThat(file.packageName()).isEqualTo("com.acme.model");
        assertThat(file.className()).isEqualTo("Product");
        assertThat(file.content()).isEqualTo("class Product {}\n");
    }

    @Test
    @DisplayName("Edge: relativePath for package a.b and class C is a/b/C.java")
    void relativePath_twoSegmentPackage_isSlashSeparated() {
        assertThat(new JavaFile("a.b", "C", "x").relativePath()).isEqualTo("a/b/C.java");
    }

    @Test
    @DisplayName("relativePath for com.acme.model and Product is com/acme/model/Product.java")
    void relativePath_referenceExample() {
        assertThat(new JavaFile("com.acme.model", "Product", "x").relativePath())
                .isEqualTo("com/acme/model/Product.java");
    }

    @Test
    @DisplayName("relativePath for a single-segment package")
    void relativePath_singleSegmentPackage() {
        assertThat(new JavaFile("acme", "Root", "x").relativePath()).isEqualTo("acme/Root.java");
    }

    @Test
    @DisplayName("Two JavaFiles with equal components are equal")
    void equals_equalComponents_areEqual() {
        JavaFile first = new JavaFile("a.b", "C", "x\n");
        JavaFile second = new JavaFile("a.b", "C", "x\n");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    @DisplayName("Edge: null packageName throws NullPointerException")
    void constructor_nullPackageName_throwsNullPointerException() {
        assertThatThrownBy(() -> new JavaFile(null, "A", "x")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: null className throws NullPointerException")
    void constructor_nullClassName_throwsNullPointerException() {
        assertThatThrownBy(() -> new JavaFile("a.b", null, "x")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: null content throws NullPointerException")
    void constructor_nullContent_throwsNullPointerException() {
        assertThatThrownBy(() -> new JavaFile("a.b", "A", null)).isInstanceOf(NullPointerException.class);
    }
}
