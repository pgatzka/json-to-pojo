package io.github.pgatzka.codegen;

import io.github.pgatzka.JsonType;
import io.github.pgatzka.TypedJsonPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Generates mutable Java POJOs from the typed path list of one JSON document. Not instantiable. */
public final class PojoGenerator {

    private static final String ROOT = "$";

    private static final String GENERATOR_NAME = "io.github.pgatzka.json-pojo-gen";

    private PojoGenerator() {
        throw new AssertionError("No instances");
    }

    /**
     * Generates one {@link JavaFile} per JSON object in {@code paths}: the root class first, then one class per
     * object path in first-occurrence order.
     *
     * <p>The getter and setter of a member are annotated {@code @JsonProperty("<key>")} whenever the property name
     * Jackson derives from the getter (strip {@code get}/{@code is}, lower-case the leading run of upper-case
     * characters) differs from the JSON key, so keys such as {@code Id} or {@code URL} are annotated although they
     * are kept as field names. Fields are never annotated. Keys {@code Objects}, {@code Double} and {@code java} get
     * a {@code _} appended because such fields would shadow qualifiers used by the generated equals/hashCode.
     *
     * @param paths   the typed paths of one JSON document, as returned by {@code JsonPathLister.listPaths(json,
     *                options.mapPaths())}; must not be null
     * @param options the generator options; must not be null
     * @return an unmodifiable list of generated files; never null
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the root is not an object (message starts with "Root must be a JSON
     *         object"), including when {@code options.mapPaths()} contains {@code $} (message starts with "Root must
     *         be a JSON object, not a map path"); if two different object paths resolve to the same class name
     *         (message starts with "Class name &lt;X&gt; used by both"); if two class names differ only in case
     *         (message starts with "Class name &lt;X&gt; collides with &lt;Y&gt;" and names both paths); if a member
     *         has the empty key {@code ""} (message starts with "Empty JSON key at &lt;parent path&gt;"); if a derived class name is the simple name of a public
     *         {@code java.lang} type, one of List/Map/Objects/JsonProperty/Generated, or one of
     *         var/yield/record/sealed/permits (message starts with "Class name &lt;X&gt; is reserved"); if a
     *         classNames key is not an object path of the document (message starts with "classNames path not
     *         found"); if a class name cannot be derived from a key (message starts with "Cannot derive class name
     *         for"); if two members of one object map to the same field name (message starts with
     *         "Field name &lt;f&gt; used by both"); if a member's capitalized name is {@code Class}, whose getter
     *         would clash with {@code Object.getClass()} (message starts with "Field name Class conflicts with
     *         Object.getClass()"); if two members with different field names map to the same accessor names, e.g.
     *         keys {@code a} and {@code A} (message starts with "Accessor name &lt;getX&gt; used by both"); if a path
     *         in the list is malformed (message starts with "Invalid path")
     */
    public static List<JavaFile> generate(List<TypedJsonPath> paths, GeneratorOptions options) {
        Objects.requireNonNull(paths, "paths must not be null");
        Objects.requireNonNull(options, "options must not be null");
        if (options.mapPaths().contains(ROOT)) {
            throw new IllegalArgumentException("Root must be a JSON object, not a map path");
        }
        Map<String, TypedJsonPath> byPath = new LinkedHashMap<>();
        for (TypedJsonPath typed : paths) {
            Objects.requireNonNull(typed, "paths must not contain null");
            String path = typed.path();
            if (path.startsWith("$[*]")) {
                throw new IllegalArgumentException(
                        "Root must be a JSON object, but the document contains the path " + path);
            }
            if (!path.startsWith("$.") && !path.startsWith("$['")) {
                throw new IllegalArgumentException(
                        "Invalid path: " + path + " (expected a path starting with \"$.\" or \"$['\")");
            }
            // Validates the whole path up front so a malformed entry fails before any class is planned.
            List<Segment> segments = parse(path);
            rejectEmptyKeys(path, segments);
            byPath.putIfAbsent(path, typed);
        }

        List<String> objectPaths = new ArrayList<>();
        objectPaths.add(ROOT);
        for (TypedJsonPath typed : byPath.values()) {
            if (typed.type() == JsonType.OBJECT && !options.mapPaths().contains(typed.path())) {
                objectPaths.add(typed.path());
            }
        }
        for (String path : options.classNames().keySet()) {
            if (!objectPaths.contains(path)) {
                throw new IllegalArgumentException("classNames path not found: " + path);
            }
        }

        Map<String, String> classNames = new LinkedHashMap<>();
        Map<String, String> pathsByClassName = new HashMap<>();
        Map<String, String> namesByFoldedName = new HashMap<>();
        for (String path : objectPaths) {
            String name = className(path, options);
            String previous = pathsByClassName.putIfAbsent(name, path);
            if (previous != null) {
                throw new IllegalArgumentException("Class name " + name + " used by both " + previous + " and " + path
                        + "; resolve it with classNames");
            }
            // Source files of names differing only in case overwrite each other on case-insensitive file systems.
            String folded = name.toLowerCase(Locale.ROOT);
            String other = namesByFoldedName.putIfAbsent(folded, name);
            if (other != null) {
                throw new IllegalArgumentException("Class name " + name + " collides with " + other + " (paths "
                        + pathsByClassName.get(other) + " and " + path
                        + ") on case-insensitive file systems; resolve it with classNames");
            }
            classNames.put(path, name);
        }

        Resolver resolver = new Resolver(byPath, classNames, options);
        Map<String, List<Field>> fields = new LinkedHashMap<>();
        for (String path : objectPaths) {
            fields.put(path, new ArrayList<>());
        }
        Map<String, Map<String, String>> fieldOwners = new HashMap<>();
        Map<String, Map<String, String>> accessorOwners = new HashMap<>();
        for (TypedJsonPath typed : byPath.values()) {
            List<Segment> segments = parse(typed.path());
            Segment last = segments.getLast();
            if (last.kind() != SegmentKind.MEMBER) {
                continue;
            }
            String parent =
                    typed.path().substring(0, typed.path().length() - last.raw().length());
            List<Field> owner = fields.get(parent);
            if (owner == null) {
                continue;
            }
            String fieldName = JavaNames.fieldName(last.key());
            String previous =
                    fieldOwners.computeIfAbsent(parent, p -> new HashMap<>()).putIfAbsent(fieldName, typed.path());
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Field name " + fieldName + " used by both " + previous + " and " + typed.path());
            }
            String suffix = JavaNames.capitalize(fieldName);
            if (suffix.equals("Class")) {
                throw new IllegalArgumentException(
                        "Field name " + fieldName + " conflicts with Object.getClass() (member " + typed.path() + ")");
            }
            boolean boxed = typed.nullable() || typed.optional();
            String type = resolver.javaType(typed.path(), boxed);
            String getter = (type.equals("boolean") ? "is" : "get") + suffix;
            // Keyed by the capitalized name: getters and setters of both members would clash (or overload
            // ambiguously for Jackson) whatever their prefixes and parameter types are.
            String previousAccessor =
                    accessorOwners.computeIfAbsent(parent, p -> new HashMap<>()).putIfAbsent(suffix, typed.path());
            if (previousAccessor != null) {
                throw new IllegalArgumentException(
                        "Accessor name " + getter + " used by both " + previousAccessor + " and " + typed.path());
            }
            owner.add(new Field(fieldName, last.key(), type, getter));
        }

        List<JavaFile> files = new ArrayList<>(objectPaths.size());
        for (String path : objectPaths) {
            String name = classNames.get(path);
            String content = render(options, name, fields.get(path));
            files.add(new JavaFile(options.packageName(), name, content));
        }
        return List.copyOf(files);
    }

    /**
     * Rejects members with the key {@code ""}: Jackson reads {@code @JsonProperty("")} as "use the default name",
     * so such a key cannot be bound to any field.
     */
    private static void rejectEmptyKeys(String path, List<Segment> segments) {
        int offset = 1;
        for (Segment segment : segments) {
            if (segment.kind() == SegmentKind.MEMBER && segment.key().isEmpty()) {
                throw new IllegalArgumentException(
                        "Empty JSON key at " + path.substring(0, offset) + " (path " + path + ") cannot be mapped");
            }
            offset += segment.raw().length();
        }
    }

    private static String className(String path, GeneratorOptions options) {
        String override = options.classNames().get(path);
        if (override != null) {
            return override;
        }
        if (path.equals(ROOT)) {
            return options.rootClassName();
        }
        List<Segment> segments = parse(path);
        int end = segments.size();
        while (end > 0 && segments.get(end - 1).kind() != SegmentKind.MEMBER) {
            end--;
        }
        if (end == 0) {
            throw new IllegalArgumentException(
                    "Cannot derive class name for " + path + ": no member key; use classNames");
        }
        String key = segments.get(end - 1).key();
        String word = end < segments.size() ? JavaNames.singularize(key) : key;
        String name = JavaNames.className(word);
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot derive class name for " + path + " from key '" + key + "'; use classNames");
        }
        if (!JavaNames.isValidClassName(name) || JavaNames.isReservedClassName(name)) {
            throw new IllegalArgumentException("Class name " + name + " is reserved (derived for " + path
                    + "); choose another name via classNames");
        }
        return name;
    }

    private static String render(GeneratorOptions options, String name, List<Field> fields) {
        boolean usesJsonProperty = fields.stream().anyMatch(Field::needsJsonProperty);
        boolean usesList = fields.stream().anyMatch(f -> f.type().contains("List<"));
        boolean usesMap = fields.stream().anyMatch(f -> f.type().contains("Map<"));
        boolean usesObjects = options.generateEqualsAndHashCode();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(options.packageName()).append(";\n\n");
        if (usesJsonProperty) {
            sb.append("import com.fasterxml.jackson.annotation.JsonProperty;\n");
        }
        if (usesList) {
            sb.append("import java.util.List;\n");
        }
        if (usesMap) {
            sb.append("import java.util.Map;\n");
        }
        if (usesObjects) {
            sb.append("import java.util.Objects;\n");
        }
        sb.append("import javax.annotation.processing.Generated;\n\n");
        sb.append("@Generated(\"").append(GENERATOR_NAME).append("\")\n");
        sb.append("public class ").append(name).append(" {\n");

        // Fields stay unannotated: Jackson would treat an annotated private field as a property of its own, next to
        // the one derived from the accessors, and serialize keys such as "Id" twice.
        for (Field field : fields) {
            sb.append('\n');
            sb.append("    private ")
                    .append(field.type())
                    .append(' ')
                    .append(field.name())
                    .append(";\n");
        }

        sb.append("\n    public ").append(name).append("() {}\n");

        for (Field field : fields) {
            String suffix = JavaNames.capitalize(field.name());
            String getter = field.getter();
            String annotation = field.needsJsonProperty()
                    ? "    @JsonProperty(" + JavaNames.stringLiteral(field.key()) + ")\n"
                    : "";
            sb.append('\n')
                    .append(annotation)
                    .append("    public ")
                    .append(field.type())
                    .append(' ')
                    .append(getter)
                    .append("() {\n")
                    .append("        return this.")
                    .append(field.name())
                    .append(";\n")
                    .append("    }\n");
            sb.append('\n')
                    .append(annotation)
                    .append("    public void set")
                    .append(suffix)
                    .append('(')
                    .append(field.type())
                    .append(' ')
                    .append(field.name())
                    .append(") {\n")
                    .append("        this.")
                    .append(field.name())
                    .append(" = ")
                    .append(field.name())
                    .append(";\n")
                    .append("    }\n");
        }

        if (options.generateEqualsAndHashCode()) {
            renderEqualsAndHashCode(sb, name, fields);
        }
        if (options.generateToString()) {
            renderToString(sb, name, fields);
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static void renderEqualsAndHashCode(StringBuilder sb, String name, List<Field> fields) {
        sb.append("\n    @Override\n")
                .append("    public boolean equals(Object o) {\n")
                .append("        if (this == o) {\n")
                .append("            return true;\n")
                .append("        }\n")
                .append("        if (!(o instanceof ")
                .append(name)
                .append(")) {\n")
                .append("            return false;\n")
                .append("        }\n");
        if (fields.isEmpty()) {
            sb.append("        return true;\n");
        } else {
            sb.append("        ").append(name).append(" other = (").append(name).append(") o;\n");
            sb.append("        return ");
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    sb.append("\n                && ");
                }
                Field field = fields.get(i);
                String mine = "this." + field.name();
                String theirs = "other." + field.name();
                switch (field.type()) {
                    case "long", "boolean" -> sb.append(mine).append(" == ").append(theirs);
                    case "double" ->
                        sb.append("Double.compare(")
                                .append(mine)
                                .append(", ")
                                .append(theirs)
                                .append(") == 0");
                    default ->
                        sb.append("Objects.equals(")
                                .append(mine)
                                .append(", ")
                                .append(theirs)
                                .append(')');
                }
            }
            sb.append(";\n");
        }
        sb.append("    }\n");

        sb.append("\n    @Override\n").append("    public int hashCode() {\n").append("        return Objects.hash(");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("this.").append(fields.get(i).name());
        }
        sb.append(");\n").append("    }\n");
    }

    private static void renderToString(StringBuilder sb, String name, List<Field> fields) {
        sb.append("\n    @Override\n")
                .append("    public String toString() {\n")
                .append("        return \"")
                .append(name)
                .append("{\"");
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            sb.append("\n                + \"")
                    .append(i > 0 ? ", " : "")
                    .append(field.name())
                    .append("=\" + this.")
                    .append(field.name());
        }
        sb.append("\n                + \"}\";\n").append("    }\n");
    }

    /** Parses a collapsed path as produced by the lister into its segments after the leading {@code $}. */
    private static List<Segment> parse(String path) {
        List<Segment> segments = new ArrayList<>();
        int i = 1;
        int length = path.length();
        while (i < length) {
            if (path.startsWith("[*]", i)) {
                segments.add(new Segment("[*]", SegmentKind.WILDCARD, null));
                i += 3;
            } else if (path.startsWith(".*", i)) {
                segments.add(new Segment(".*", SegmentKind.WILDCARD, null));
                i += 2;
            } else if (path.startsWith("['", i)) {
                StringBuilder key = new StringBuilder();
                int j = i + 2;
                while (j < length && path.charAt(j) != '\'') {
                    char c = path.charAt(j);
                    if (c == '\\' && j + 1 < length) {
                        key.append(path.charAt(j + 1));
                        j += 2;
                    } else {
                        key.append(c);
                        j++;
                    }
                }
                if (!path.startsWith("']", j)) {
                    throw new IllegalArgumentException("Invalid path: " + path);
                }
                j += 2;
                segments.add(new Segment(path.substring(i, j), SegmentKind.MEMBER, key.toString()));
                i = j;
            } else if (path.charAt(i) == '.') {
                int j = i + 1;
                while (j < length && path.charAt(j) != '.' && path.charAt(j) != '[') {
                    j++;
                }
                if (j == i + 1) {
                    throw new IllegalArgumentException("Invalid path: " + path);
                }
                segments.add(new Segment(path.substring(i, j), SegmentKind.MEMBER, path.substring(i + 1, j)));
                i = j;
            } else {
                throw new IllegalArgumentException("Invalid path: " + path);
            }
        }
        return segments;
    }

    private enum SegmentKind {
        MEMBER,
        WILDCARD
    }

    /** One path segment: its raw text and, for members, the unescaped key. */
    private record Segment(String raw, SegmentKind kind, String key) {}

    /** One generated field. */
    private record Field(String name, String key, String type, String getter) {
        /** Jackson names the property after the getter, so the key must be pinned whenever that name differs. */
        boolean needsJsonProperty() {
            return !JavaNames.jacksonPropertyName(getter).equals(key);
        }
    }

    /** Maps paths to Java type names. */
    private static final class Resolver {
        private final Map<String, TypedJsonPath> byPath;
        private final Map<String, String> classNames;
        private final GeneratorOptions options;

        private Resolver(Map<String, TypedJsonPath> byPath, Map<String, String> classNames, GeneratorOptions options) {
            this.byPath = byPath;
            this.classNames = classNames;
            this.options = options;
        }

        String javaType(String path, boolean boxed) {
            if (options.mapPaths().contains(path)) {
                return "Map<String, " + referenceType(path + ".*") + ">";
            }
            TypedJsonPath typed = byPath.get(path);
            if (typed == null) {
                return "Object";
            }
            return switch (typed.type()) {
                case ARRAY -> "List<" + referenceType(path + "[*]") + ">";
                case OBJECT -> classNames.get(path);
                case STRING -> "String";
                case BOOLEAN -> boxed ? "Boolean" : "boolean";
                case INTEGER -> boxed ? "Long" : "long";
                case FLOAT -> boxed ? "Double" : "double";
                case NULL -> "Object";
            };
        }

        private String referenceType(String path) {
            return javaType(path, true);
        }
    }
}
