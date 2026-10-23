package io.github.pgatzka.codegen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Options for {@link PojoGenerator}.
 *
 * @param packageName               Java package of all generated classes, e.g. "com.acme.model"; never null
 * @param rootClassName             class name for the root object; never null
 * @param mapPaths                  collapsed paths of objects treated as maps (same set given to JsonPathLister);
 *                                  never null
 * @param classNames                collapsed object path to class name overrides; never null, may be empty
 * @param generateEqualsAndHashCode whether to emit equals(Object) and hashCode()
 * @param generateToString          whether to emit toString()
 */
public record GeneratorOptions(
        String packageName,
        String rootClassName,
        Set<String> mapPaths,
        Map<String, String> classNames,
        boolean generateEqualsAndHashCode,
        boolean generateToString) {

    /**
     * Validates the options and copies {@code mapPaths} and {@code classNames} into unmodifiable collections.
     *
     * @param packageName               Java package of all generated classes; must not be null
     * @param rootClassName             class name for the root object; must not be null
     * @param mapPaths                  collapsed paths of objects treated as maps; must not be null nor contain null
     * @param classNames                collapsed object path to class name overrides; must not be null nor contain
     *                                  null keys or values
     * @param generateEqualsAndHashCode whether to emit equals(Object) and hashCode()
     * @param generateToString          whether to emit toString()
     * @throws NullPointerException if any argument is null, or a collection contains null
     * @throws IllegalArgumentException if {@code packageName} is not a valid package name (message starts with
     *         "Invalid package name"); if {@code rootClassName} or a {@code classNames} value is not a Java
     *         identifier, is a keyword or is one of var/yield/record/sealed/permits (message starts with
     *         "Invalid class name"); if {@code rootClassName} or a {@code classNames} value is the simple name of a
     *         public {@code java.lang} type or one of List/Map/Objects/JsonProperty/Generated (message starts with
     *         "Class name &lt;X&gt; is reserved"); if a {@code classNames} key or {@code mapPaths}
     *         element does not start with '$' (message starts with "Invalid path")
     */
    public GeneratorOptions {
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(rootClassName, "rootClassName must not be null");
        Objects.requireNonNull(mapPaths, "mapPaths must not be null");
        Objects.requireNonNull(classNames, "classNames must not be null");
        // Iterate instead of contains(null): immutable collections reject null lookups with an NPE of their own.
        for (String path : mapPaths) {
            Objects.requireNonNull(path, "mapPaths must not contain null");
        }
        for (Map.Entry<String, String> entry : classNames.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "classNames must not contain a null key");
            Objects.requireNonNull(entry.getValue(), "classNames must not contain a null value");
        }
        if (!JavaNames.isValidPackageName(packageName)) {
            throw new IllegalArgumentException("Invalid package name '" + packageName + "'");
        }
        requireClassName(rootClassName);
        for (String path : mapPaths) {
            requirePath(path);
        }
        for (Map.Entry<String, String> entry : classNames.entrySet()) {
            requirePath(entry.getKey());
            requireClassName(entry.getValue());
        }
        mapPaths = Collections.unmodifiableSet(new LinkedHashSet<>(mapPaths));
        classNames = Collections.unmodifiableMap(new LinkedHashMap<>(classNames));
    }

    /**
     * Creates options with no map paths, no class name overrides and both flags enabled; equivalent to
     * {@code new GeneratorOptions(packageName, rootClassName, Set.of(), Map.of(), true, true)}.
     *
     * @param packageName   Java package of all generated classes; must not be null
     * @param rootClassName class name for the root object; must not be null
     * @return the options; never null
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if a name is invalid, as for the canonical constructor
     */
    public static GeneratorOptions defaults(String packageName, String rootClassName) {
        return new GeneratorOptions(packageName, rootClassName, Set.of(), Map.of(), true, true);
    }

    private static void requireClassName(String name) {
        if (!JavaNames.isValidClassName(name)) {
            throw new IllegalArgumentException("Invalid class name '" + name + "'");
        }
        if (JavaNames.isReservedClassName(name)) {
            throw new IllegalArgumentException("Class name " + name
                    + " is reserved: it would shadow a java.lang type or an import of the generated code");
        }
    }

    private static void requirePath(String path) {
        if (!path.startsWith("$")) {
            throw new IllegalArgumentException("Invalid path '" + path + "': must start with '$'");
        }
    }
}
