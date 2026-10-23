package io.github.pgatzka;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/**
 * Configuration for the json-pojo-gen plugin, registered as the {@code jsonPojoGen} extension.
 */
public abstract class JsonPojoGenExtension {

    /** Name under which the extension is registered. Value: "jsonPojoGen". Never null. */
    public static final String NAME = "jsonPojoGen";

    /**
     * Creates the extension; Gradle instantiates it as a managed object.
     */
    public JsonPojoGenExtension() {}

    /**
     * Returns the JSON file to process.
     *
     * @return the input file property; has no default value; never null
     */
    public abstract RegularFileProperty getInputFile();

    /**
     * Returns the JSON paths of objects to treat as maps.
     *
     * @return the map paths property; empty by default (the property has a value); never null
     */
    public abstract SetProperty<String> getMapPaths();

    /**
     * Returns the Java package of the generated classes.
     *
     * @return the package name property; required, has no default value; never null
     */
    public abstract Property<String> getPackageName();

    /**
     * Returns the class name of the root object.
     *
     * @return the root class name property; by convention the input file's base name in UpperCamelCase; never null
     */
    public abstract Property<String> getRootClassName();

    /**
     * Returns the object path to class name overrides.
     *
     * @return the class names property; empty by default; never null
     */
    public abstract MapProperty<String, String> getClassNames();

    /**
     * Returns whether equals/hashCode are generated.
     *
     * @return the flag property; {@code true} by convention; never null
     */
    public abstract Property<Boolean> getGenerateEqualsAndHashCode();

    /**
     * Returns whether toString is generated.
     *
     * @return the flag property; {@code true} by convention; never null
     */
    public abstract Property<Boolean> getGenerateToString();

    /**
     * Returns the directory the generated sources are written to.
     *
     * @return the output directory property; by convention
     *         {@code build/generated/sources/jsonPojoGen/java/main}; never null
     */
    public abstract DirectoryProperty getOutputDirectory();
}
