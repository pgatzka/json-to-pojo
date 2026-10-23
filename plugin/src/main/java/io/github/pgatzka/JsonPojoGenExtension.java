package io.github.pgatzka;

import org.gradle.api.file.RegularFileProperty;
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
}
