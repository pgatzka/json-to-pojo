package io.github.pgatzka;

import java.util.Objects;

/**
 * A collapsed JSON path with its merged type and nullability/optionality.
 *
 * @param path     the collapsed path, e.g. "$.items[*].sku"; never null
 * @param type     the merged type; never null
 * @param nullable true if any node at this path is JSON null
 * @param optional true if at least one merged parent object lacks this member
 */
public record TypedJsonPath(String path, JsonType type, boolean nullable, boolean optional) {

    /**
     * Creates a typed path.
     *
     * @param path     the collapsed path; must not be null
     * @param type     the merged type; must not be null
     * @param nullable true if any node at this path is JSON null
     * @param optional true if at least one merged parent object lacks this member
     * @throws NullPointerException if {@code path} or {@code type} is null
     */
    public TypedJsonPath {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    /**
     * Creates a typed path that is neither nullable nor optional;
     * equivalent to {@code new TypedJsonPath(path, type, false, false)}.
     *
     * @param path the collapsed path; must not be null
     * @param type the merged type; must not be null
     * @throws NullPointerException if {@code path} or {@code type} is null
     */
    public TypedJsonPath(String path, JsonType type) {
        this(path, type, false, false);
    }

    /**
     * Returns {@code path + ": " + type.label()} followed by "" when neither flag is set,
     * " (nullable)", " (optional)", or " (nullable, optional)".
     *
     * @return the path, the type label and the flag suffix; never null
     */
    @Override
    public String toString() {
        String base = path + ": " + type.label();
        if (nullable && optional) {
            return base + " (nullable, optional)";
        }
        if (nullable) {
            return base + " (nullable)";
        }
        if (optional) {
            return base + " (optional)";
        }
        return base;
    }
}
