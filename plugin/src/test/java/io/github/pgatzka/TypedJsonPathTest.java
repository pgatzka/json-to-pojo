package io.github.pgatzka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TypedJsonPath}.
 */
class TypedJsonPathTest {

    @Test
    @DisplayName("7: toString is '<path>: <label>'")
    void toString_isPathColonLabel() {
        assertThat(new TypedJsonPath("$.id", JsonType.INTEGER).toString()).isEqualTo("$.id: integer");
    }

    @Test
    @DisplayName("7: toString uses the type label for every type")
    void toString_usesLabelForEveryType() {
        for (JsonType type : JsonType.values()) {
            assertThat(new TypedJsonPath("$.items[*].sku", type).toString())
                    .isEqualTo("$.items[*].sku: " + type.label());
        }
    }

    @Test
    @DisplayName("7: toString of a null-typed path")
    void toString_nullType() {
        assertThat(new TypedJsonPath("$.none", JsonType.NULL).toString()).isEqualTo("$.none: null");
    }

    @Test
    @DisplayName("7: two TypedJsonPath with equal components are equal")
    void equals_equalComponents_areEqual() {
        TypedJsonPath first = new TypedJsonPath("$.id", JsonType.INTEGER);
        TypedJsonPath second = new TypedJsonPath("$.id", JsonType.INTEGER);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    @DisplayName("7: different type makes TypedJsonPath unequal")
    void equals_differentType_areNotEqual() {
        assertThat(new TypedJsonPath("$.id", JsonType.INTEGER)).isNotEqualTo(new TypedJsonPath("$.id", JsonType.FLOAT));
    }

    @Test
    @DisplayName("7: different path makes TypedJsonPath unequal")
    void equals_differentPath_areNotEqual() {
        assertThat(new TypedJsonPath("$.id", JsonType.INTEGER))
                .isNotEqualTo(new TypedJsonPath("$.other", JsonType.INTEGER));
    }

    @Test
    @DisplayName("Accessors return the constructor arguments")
    void accessors_returnComponents() {
        TypedJsonPath typed = new TypedJsonPath("$.items[*].qty", JsonType.FLOAT);

        assertThat(typed.path()).isEqualTo("$.items[*].qty");
        assertThat(typed.type()).isEqualTo(JsonType.FLOAT);
    }

    @Test
    @DisplayName("Edge: null path throws NullPointerException")
    void constructor_nullPath_throwsNullPointerException() {
        assertThatThrownBy(() -> new TypedJsonPath(null, JsonType.NULL)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: null type throws NullPointerException")
    void constructor_nullType_throwsNullPointerException() {
        assertThatThrownBy(() -> new TypedJsonPath("$.a", null)).isInstanceOf(NullPointerException.class);
    }
}
