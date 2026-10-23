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

    // ---------------------------------------------------------------------------------------------
    // nullable-optional-paths (docs/specs/nullable-optional-paths.md)
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("nullable 10: two-argument constructor equals the canonical constructor with both flags false")
    void twoArgumentConstructor_equalsCanonicalWithFlagsFalse() {
        TypedJsonPath shortForm = new TypedJsonPath("$.x", JsonType.INTEGER);
        TypedJsonPath canonical = new TypedJsonPath("$.x", JsonType.INTEGER, false, false);

        assertThat(shortForm).isEqualTo(canonical);
        assertThat(shortForm.hashCode()).isEqualTo(canonical.hashCode());
        assertThat(shortForm.nullable()).isFalse();
        assertThat(shortForm.optional()).isFalse();
    }

    @Test
    @DisplayName("nullable 10: unflagged path prints without suffix")
    void toString_noFlags_hasNoSuffix() {
        assertThat(new TypedJsonPath("$.x", JsonType.INTEGER).toString()).isEqualTo("$.x: integer");
        assertThat(new TypedJsonPath("$.x", JsonType.STRING, false, false).toString())
                .isEqualTo("$.x: string");
    }

    @Test
    @DisplayName("nullable 10: nullable path prints ' (nullable)'")
    void toString_nullable_appendsNullableSuffix() {
        assertThat(new TypedJsonPath("$.x", JsonType.STRING, true, false).toString())
                .isEqualTo("$.x: string (nullable)");
    }

    @Test
    @DisplayName("nullable 10: optional path prints ' (optional)'")
    void toString_optional_appendsOptionalSuffix() {
        assertThat(new TypedJsonPath("$.x", JsonType.STRING, false, true).toString())
                .isEqualTo("$.x: string (optional)");
    }

    @Test
    @DisplayName("nullable 10: nullable and optional path prints ' (nullable, optional)'")
    void toString_nullableAndOptional_appendsBothSuffix() {
        assertThat(new TypedJsonPath("$.x", JsonType.STRING, true, true).toString())
                .isEqualTo("$.x: string (nullable, optional)");
    }

    @Test
    @DisplayName("nullable 10: suffixes use the type label for every type")
    void toString_flagsWithEveryType_useLabel() {
        for (JsonType type : JsonType.values()) {
            assertThat(new TypedJsonPath("$[*].a", type, true, true).toString())
                    .isEqualTo("$[*].a: " + type.label() + " (nullable, optional)");
        }
    }

    @Test
    @DisplayName("nullable 10: a null-typed nullable path prints 'null (nullable)'")
    void toString_nullTypeNullable() {
        assertThat(new TypedJsonPath("$.none", JsonType.NULL, true, false).toString())
                .isEqualTo("$.none: null (nullable)");
    }

    @Test
    @DisplayName("nullable: accessors return all four constructor arguments")
    void accessors_returnAllFourComponents() {
        TypedJsonPath typed = new TypedJsonPath("$.items[*].note", JsonType.STRING, true, false);

        assertThat(typed.path()).isEqualTo("$.items[*].note");
        assertThat(typed.type()).isEqualTo(JsonType.STRING);
        assertThat(typed.nullable()).isTrue();
        assertThat(typed.optional()).isFalse();

        TypedJsonPath optional = new TypedJsonPath("$.items[*].price", JsonType.FLOAT, false, true);

        assertThat(optional.nullable()).isFalse();
        assertThat(optional.optional()).isTrue();
    }

    @Test
    @DisplayName("nullable Edge: equality includes the nullable flag")
    void equals_differentNullable_areNotEqual() {
        assertThat(new TypedJsonPath("$.x", JsonType.INTEGER, true, false))
                .isNotEqualTo(new TypedJsonPath("$.x", JsonType.INTEGER));
    }

    @Test
    @DisplayName("nullable Edge: equality includes the optional flag")
    void equals_differentOptional_areNotEqual() {
        assertThat(new TypedJsonPath("$.x", JsonType.INTEGER, false, true))
                .isNotEqualTo(new TypedJsonPath("$.x", JsonType.INTEGER));
        assertThat(new TypedJsonPath("$.x", JsonType.INTEGER, false, true))
                .isNotEqualTo(new TypedJsonPath("$.x", JsonType.INTEGER, true, false));
    }

    @Test
    @DisplayName("nullable Edge: equal components including flags are equal")
    void equals_equalComponentsWithFlags_areEqual() {
        TypedJsonPath first = new TypedJsonPath("$.x", JsonType.INTEGER, true, true);
        TypedJsonPath second = new TypedJsonPath("$.x", JsonType.INTEGER, true, true);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    @DisplayName("nullable Edge: canonical constructor with null path throws NullPointerException")
    void canonicalConstructor_nullPath_throwsNullPointerException() {
        assertThatThrownBy(() -> new TypedJsonPath(null, JsonType.NULL, true, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("nullable Edge: canonical constructor with null type throws NullPointerException")
    void canonicalConstructor_nullType_throwsNullPointerException() {
        assertThatThrownBy(() -> new TypedJsonPath("$.a", null, false, false)).isInstanceOf(NullPointerException.class);
    }
}
