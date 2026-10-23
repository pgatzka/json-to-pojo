package io.github.pgatzka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Computes the typed JSON paths of every node in a JSON document. Not instantiable.
 */
public final class JsonPathLister {

    private static final String ROOT = "$";

    private static final String ARRAY_WILDCARD = "[*]";

    private static final String MAP_WILDCARD = ".*";

    private static final String CONFLICT_PREFIX = "Conflicting types at ";

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    // Trailing tokens after the root value would otherwise be silently ignored and hide malformed input.
    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonPathLister() {
        throw new AssertionError("No instances");
    }

    /**
     * Lists the typed JSON paths of all nodes below the root of {@code json}, in pre-order and document order,
     * with array elements collapsed to {@code [*]} and duplicates merged (first occurrence keeps the position).
     * Equivalent to {@code listPaths(json, Set.of())}.
     *
     * @param json the JSON document text; must not be null
     * @return an unmodifiable list of typed paths; never null; empty if the root is a scalar or an empty container
     * @throws NullPointerException if {@code json} is null
     * @throws IllegalArgumentException if {@code json} is blank (message contains "empty"), is not valid JSON
     *         (message starts with "Invalid JSON: "; the Jackson exception is the cause), or a collapsed path mixes
     *         a container type with another type (message starts with "Conflicting types at ")
     */
    public static List<TypedJsonPath> listPaths(String json) {
        return listPaths(json, Set.of());
    }

    /**
     * Reads {@code file} as UTF-8 and delegates to {@link #listPaths(String)}.
     * Equivalent to {@code listPaths(file, Set.of())}.
     *
     * @param file the JSON file to read; must not be null
     * @return an unmodifiable list of typed paths, as for {@link #listPaths(String)}
     * @throws NullPointerException if {@code file} is null
     * @throws IOException if the file cannot be read (e.g. does not exist)
     * @throws IllegalArgumentException as for {@link #listPaths(String)}
     */
    public static List<TypedJsonPath> listPaths(Path file) throws IOException {
        return listPaths(file, Set.of());
    }

    /**
     * Like {@link #listPaths(String)}, but every object whose collapsed path is contained in {@code mapPaths}
     * is treated as a map: its members are collapsed to {@code <path>.*} and their value paths and types merged
     * (first occurrence keeps the position), exactly as array elements are collapsed to {@code [*]}.
     *
     * @param json the JSON document text; must not be null
     * @param mapPaths collapsed paths of the objects to treat as maps; must not be null nor contain null
     * @return an unmodifiable list of typed paths; never null
     * @throws NullPointerException if {@code json} or {@code mapPaths} is null, or {@code mapPaths} contains null
     * @throws IllegalArgumentException if {@code json} is blank or invalid; if a map path is blank or does not
     *         start with {@code $} (message starts with "Invalid map path"); if a map path matches no node
     *         (message starts with "Map path &lt;p&gt; not found"); if a map path matches an array or a non-null
     *         scalar (message starts with "Map path &lt;p&gt; does not point to a JSON object"); or if an object or
     *         array is merged with any other non-null type (message exactly
     *         "Conflicting types at &lt;path&gt;: &lt;labels&gt;", labels in first-seen order, null excluded)
     */
    public static List<TypedJsonPath> listPaths(String json, Set<String> mapPaths) {
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
        Map<String, Set<JsonType>> seen = new LinkedHashMap<>();
        Set<String> matched = new HashSet<>();
        visit(root, ROOT, seen, mapPaths, matched);
        for (String mapPath : mapPaths) {
            if (!matched.contains(mapPath)) {
                throw new IllegalArgumentException("Map path " + mapPath + " not found in the JSON document");
            }
        }
        List<TypedJsonPath> result = new ArrayList<>(seen.size());
        for (Map.Entry<String, Set<JsonType>> entry : seen.entrySet()) {
            result.add(new TypedJsonPath(entry.getKey(), merge(entry.getKey(), entry.getValue())));
        }
        return List.copyOf(result);
    }

    /**
     * Reads {@code file} as UTF-8 and delegates to {@link #listPaths(String, Set)}.
     *
     * @param file the JSON file to read; must not be null
     * @param mapPaths collapsed paths of the objects to treat as maps; must not be null
     * @return an unmodifiable list of typed paths, as for {@link #listPaths(String, Set)}
     * @throws NullPointerException if {@code file} or {@code mapPaths} is null
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException as for {@link #listPaths(String, Set)}
     */
    public static List<TypedJsonPath> listPaths(Path file, Set<String> mapPaths) throws IOException {
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

    /**
     * Tells whether {@code e} was raised because a collapsed path mixes incompatible types.
     */
    static boolean isTypeConflict(IllegalArgumentException e) {
        String message = e.getMessage();
        return message != null && message.startsWith(CONFLICT_PREFIX);
    }

    private static JsonType merge(String path, Set<JsonType> types) {
        Set<JsonType> distinct = new LinkedHashSet<>(types);
        if (distinct.size() > 1) {
            distinct.remove(JsonType.NULL);
        }
        if (distinct.size() == 1) {
            return distinct.iterator().next();
        }
        if (distinct.contains(JsonType.OBJECT) || distinct.contains(JsonType.ARRAY)) {
            String labels = distinct.stream().map(JsonType::label).collect(Collectors.joining(", "));
            throw new IllegalArgumentException(CONFLICT_PREFIX + path + ": " + labels);
        }
        if (distinct.equals(Set.of(JsonType.INTEGER, JsonType.FLOAT))) {
            return JsonType.FLOAT;
        }
        return JsonType.STRING;
    }

    private static JsonType typeOf(JsonNode node) {
        if (node.isObject()) {
            return JsonType.OBJECT;
        }
        if (node.isArray()) {
            return JsonType.ARRAY;
        }
        if (node.isTextual()) {
            return JsonType.STRING;
        }
        if (node.isBoolean()) {
            return JsonType.BOOLEAN;
        }
        if (node.isIntegralNumber()) {
            return JsonType.INTEGER;
        }
        if (node.isFloatingPointNumber()) {
            return JsonType.FLOAT;
        }
        // Remaining node kinds (null, and missing/binary/POJO which readTree does not produce) count as null.
        return JsonType.NULL;
    }

    private static void record(String path, JsonNode node, Map<String, Set<JsonType>> seen) {
        seen.computeIfAbsent(path, p -> new LinkedHashSet<>()).add(typeOf(node));
    }

    private static void visit(
            JsonNode node, String path, Map<String, Set<JsonType>> seen, Set<String> mapPaths, Set<String> matched) {
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
                record(childPath, value, seen);
                visit(value, childPath, seen, mapPaths, matched);
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String childPath = path + memberSegment(field.getKey());
                record(childPath, field.getValue(), seen);
                visit(field.getValue(), childPath, seen, mapPaths, matched);
            }
        } else if (node.isArray()) {
            String childPath = path + ARRAY_WILDCARD;
            // All elements share one wildcard path; the map merges their sub-paths in first-occurrence order.
            for (JsonNode element : node) {
                record(childPath, element, seen);
                visit(element, childPath, seen, mapPaths, matched);
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
