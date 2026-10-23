package io.github.pgatzka.codegen;

import java.util.Objects;

/**
 * One generated Java source file.
 *
 * @param packageName the package, e.g. "com.acme.model"; never null
 * @param className   the simple class name; never null
 * @param content     the full source text, UTF-8, ends with a newline; never null
 */
public record JavaFile(String packageName, String className, String content) {

    /**
     * Creates a source file.
     *
     * @param packageName the package; must not be null
     * @param className   the simple class name; must not be null
     * @param content     the full source text; must not be null
     * @throws NullPointerException if any component is null
     */
    public JavaFile {
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(className, "className must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }

    /**
     * Returns the path of this file relative to a source root, e.g. "com/acme/model/Product.java".
     *
     * @return {@code packageName.replace('.', '/') + "/" + className + ".java"}; never null
     */
    public String relativePath() {
        return packageName.replace('.', '/') + "/" + className + ".java";
    }
}
