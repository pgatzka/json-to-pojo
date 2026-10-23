package io.github.pgatzka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Computes the JSON paths of every node in a JSON document. Not instantiable.
 */
public final class JsonPathLister {

    private static final String ROOT = "$";

    private static final String ARRAY_WILDCARD = "[*]";

    private static final String MAP_WILDCARD = ".*";

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    // Trailing tokens after the root value would otherwise be silently ignored and hide malformed input.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonPathLister() {
        throw new AssertionError("No instances");
    }

    /**
     * Lists the JSON paths of all nodes below the root of {@code json}, in pre-order and document order,
     * with array elements collapsed to {@code [*]} and duplicates removed (first occurrence wins).
     *
     * @param json the JSON document text; must not be null
     * @return an unmodifiable list of paths; never null; empty if the root is a scalar or an empty container
     * @throws NullPointerException if {@code json} is null
     * @throws IllegalArgumentException if {@code json} is blank (message contains "empty") or is not valid JSON
     *         (message starts with "Invalid JSON: "; the Jackson exception is the cause)
     */
    public static List<String> listPaths(String json) {
        return listPaths(json, Set.of());
    }

    /**
     * Reads {@code file} as UTF-8 and delegates to {@link #listPaths(String)}.
     *
     * @param file the JSON file to read; must not be null
     * @return an unmodifiable list of paths, as for {@link #listPaths(String)}
     * @throws NullPointerException if {@code file} is null
     * @throws IOException if the file cannot be read (e.g. does not exist)
     * @throws IllegalArgumentException as for {@link #listPaths(String)}
     */
    public static List<String> listPaths(Path file) throws IOException {
        return listPaths(file, Set.of());
    }

    /**
     * Like {@link #listPaths(String)}, but every object whose collapsed path is contained in {@code mapPaths}
     * is treated as a map: its members are collapsed to {@code <path>.*} and their value paths merged
     * (first occurrence wins), exactly as array elements are collapsed to {@code [*]}.
     *
     * @param json the JSON document text; must not be null
     * @param mapPaths collapsed paths of the objects to treat as maps; must not be null nor contain null
     * @return an unmodifiable list of paths; never null
     * @throws NullPointerException if {@code json} or {@code mapPaths} is null, or {@code mapPaths} contains null
     * @throws IllegalArgumentException if {@code json} is blank or invalid; if a map path is blank or does not
     *         start with {@code $} (message starts with "Invalid map path"); if a map path matches no node
     *         (message starts with "Map path &lt;p&gt; not found"); or if a map path matches an array or a non-null
     *         scalar (message starts with "Map path &lt;p&gt; does not point to a JSON object")
     */
    public static List<String> listPaths(String json, Set<String> mapPaths) {
        Objects.requireNonNull(json, "json must not be null");
        Objects.requireNonNull(mapPaths, "mapPaths must not be null");
        // Iterate instead of calling contains(null): immutable sets reject null lookups with an NPE of their own.
        for (String mapPath : mapPaths) {
            Objects.requireNonNull(mapPath, "mapPaths must not contain null");
        }
        for (String mapPath : mapPaths) {
            if (mapPath.isBlank() || !mapPath.startsWith(ROOT)) {
                throw new IllegalArgumentException(
                        "Invalid map path '" + mapPath + "': must be non-blank and start with '$'");
            }
        }
        if (json.isBlank()) {
            throw new IllegalArgumentException("JSON input is empty");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getOriginalMessage(), e);
        }
        Set<String> paths = new LinkedHashSet<>();
        Set<String> matched = new HashSet<>();
        visit(root, ROOT, paths, mapPaths, matched);
        for (String mapPath : mapPaths) {
            if (!matched.contains(mapPath)) {
                throw new IllegalArgumentException("Map path " + mapPath + " not found in the JSON document");
            }
        }
        return List.copyOf(paths);
    }

    /**
     * Reads {@code file} as UTF-8 and delegates to {@link #listPaths(String, Set)}.
     *
     * @param file the JSON file to read; must not be null
     * @param mapPaths collapsed paths of the objects to treat as maps; must not be null
     * @return an unmodifiable list of paths, as for {@link #listPaths(String, Set)}
     * @throws NullPointerException if {@code file} or {@code mapPaths} is null
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException as for {@link #listPaths(String, Set)}
     */
    public static List<String> listPaths(Path file, Set<String> mapPaths) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(mapPaths, "mapPaths must not be null");
        return listPaths(Files.readString(file, StandardCharsets.UTF_8), mapPaths);
    }

    /**
     * Tells whether {@code e} was raised because of a map path rather than because of the JSON input.
     */
    static boolean isMapPathError(IllegalArgumentException e) {
        String message = e.getMessage();
        return message != null && (message.startsWith("Invalid map path") || message.startsWith("Map path "));
    }

    private static void visit(JsonNode node, String path, Set<String> paths, Set<String> mapPaths,
            Set<String> matched) {
        if (mapPaths.contains(path)) {
            matched.add(path);
            if (node.isNull()) {
                return;
            }
            if (!node.isObject()) {
                throw new IllegalArgumentException("Map path " + path + " does not point to a JSON object (found "
                        + node.getNodeType().name().toLowerCase(Locale.ROOT) + ")");
            }
            String childPath = path + MAP_WILDCARD;
            // All member values share one wildcard path, just like array elements.
            for (JsonNode value : node) {
                paths.add(childPath);
                visit(value, childPath, paths, mapPaths, matched);
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String childPath = path + memberSegment(field.getKey());
                paths.add(childPath);
                visit(field.getValue(), childPath, paths, mapPaths, matched);
            }
        } else if (node.isArray()) {
            String childPath = path + ARRAY_WILDCARD;
            // All elements share one wildcard path; the set merges their sub-paths in first-occurrence order.
            for (JsonNode element : node) {
                paths.add(childPath);
                visit(element, childPath, paths, mapPaths, matched);
            }
        }
    }

    private static String memberSegment(String name) {
        if (IDENTIFIER.matcher(name).matches()) {
            return "." + name;
        }
        String escaped = name.replace("\\", "\\\\").replace("'", "\\'");
        return "['" + escaped + "']";
    }
}
