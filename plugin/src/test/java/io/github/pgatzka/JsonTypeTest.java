package io.github.pgatzka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JsonType}.
 */
class JsonTypeTest {

    @Test
    @DisplayName("typed-json-paths: enum declares exactly the seven types in spec order")
    void values_areTheSevenTypesInOrder() {
        assertThat(JsonType.values())
                .containsExactly(
                        JsonType.FLOAT,
                        JsonType.INTEGER,
                        JsonType.STRING,
                        JsonType.BOOLEAN,
                        JsonType.OBJECT,
                        JsonType.ARRAY,
                        JsonType.NULL);
    }

    @Test
    @DisplayName("7: FLOAT label is 'float'")
    void label_float() {
        assertThat(JsonType.FLOAT.label()).isEqualTo("float");
    }

    @Test
    @DisplayName("7: INTEGER label is 'integer'")
    void label_integer() {
        assertThat(JsonType.INTEGER.label()).isEqualTo("integer");
    }

    @Test
    @DisplayName("7: STRING label is 'string'")
    void label_string() {
        assertThat(JsonType.STRING.label()).isEqualTo("string");
    }

    @Test
    @DisplayName("7: BOOLEAN label is 'boolean'")
    void label_boolean() {
        assertThat(JsonType.BOOLEAN.label()).isEqualTo("boolean");
    }

    @Test
    @DisplayName("7: OBJECT label is 'object'")
    void label_object() {
        assertThat(JsonType.OBJECT.label()).isEqualTo("object");
    }

    @Test
    @DisplayName("7: ARRAY label is 'array'")
    void label_array() {
        assertThat(JsonType.ARRAY.label()).isEqualTo("array");
    }

    @Test
    @DisplayName("7: NULL label is 'null'")
    void label_null() {
        assertThat(JsonType.NULL.label()).isEqualTo("null");
    }

    @Test
    @DisplayName("7: label is never null for any constant")
    void label_neverNull() {
        for (JsonType type : JsonType.values()) {
            assertThat(type.label()).as("label of %s", type.name()).isNotNull();
        }
    }
}
