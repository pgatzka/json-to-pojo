package io.github.pgatzka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
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
        Objects.requireNonNull(json, "json must not be null");
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
        collectChildren(root, ROOT, paths);
        return List.copyOf(paths);
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
        Objects.requireNonNull(file, "file must not be null");
        return listPaths(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static void collectChildren(JsonNode node, String path, Set<String> paths) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String childPath = path + memberSegment(field.getKey());
                paths.add(childPath);
                collectChildren(field.getValue(), childPath, paths);
            }
        } else if (node.isArray()) {
            String childPath = path + ARRAY_WILDCARD;
            // All elements share one wildcard path; the set merges their sub-paths in first-occurrence order.
            for (JsonNode element : node) {
                paths.add(childPath);
                collectChildren(element, childPath, paths);
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
