package io.github.pgatzka;

import java.util.Locale;

/** The JSON value type observed at a path. */
public enum JsonType {
    /** A number with a fraction or an exponent. */
    FLOAT,
    /** A number without a fraction or an exponent. */
    INTEGER,
    /** A JSON string. */
    STRING,
    /** {@code true} or {@code false}. */
    BOOLEAN,
    /** A JSON object. */
    OBJECT,
    /** A JSON array. */
    ARRAY,
    /** The JSON {@code null} literal. */
    NULL;

    private final String label = name().toLowerCase(Locale.ROOT);

    /**
     * Returns the lower-case name used in output: "float", "integer", "string", "boolean", "object", "array"
     * or "null".
     *
     * @return the label; never null
     */
    public String label() {
        return label;
    }
}
