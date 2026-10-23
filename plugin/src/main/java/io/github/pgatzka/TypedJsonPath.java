package io.github.pgatzka;

import java.util.Objects;

/**
 * A collapsed JSON path together with its (merged) value type.
 *
 * @param path the collapsed path, e.g. "$.items[*].sku"; never null
 * @param type the merged type; never null
 */
public record TypedJsonPath(String path, JsonType type) {

    /**
     * Creates a typed path.
     *
     * @param path the collapsed path; must not be null
     * @param type the merged type; must not be null
     * @throws NullPointerException if {@code path} or {@code type} is null
     */
    public TypedJsonPath {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    /**
     * Returns {@code path + ": " + type.label()}, e.g. "$.id: integer".
     *
     * @return the path and the type label; never null
     */
    @Override
    public String toString() {
        return path + ": " + type.label();
    }
}
