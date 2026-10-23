package io.github.pgatzka.codegen;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.lang.model.SourceVersion;

/** Java identifier rules shared by {@link GeneratorOptions} and {@link PojoGenerator}. */
final class JavaNames {

    /** Contextual keywords that are legal identifiers but not legal type names. */
    private static final Set<String> RESTRICTED_TYPE_NAMES = Set.of("var", "yield", "record", "sealed", "permits");

    /** Simple names of the types generated sources import; a class of that name would clash with the import. */
    private static final Set<String> IMPORTED_TYPE_NAMES =
            Set.of("List", "Map", "Objects", "JsonProperty", "Generated");

    /**
     * Field names that would shadow the {@code Objects}, {@code Double} and {@code java} qualifiers in generated
     * equals/hashCode, since a variable name takes precedence over a type or package name in expressions.
     */
    private static final Set<String> SHADOWING_FIELD_NAMES = Set.of("Objects", "Double", "java");

    private JavaNames() {
        throw new AssertionError("No instances");
    }

    /** Tells whether {@code name} is a syntactically valid identifier that is neither a keyword nor a literal. */
    static boolean isValidIdentifier(String name) {
        return SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name);
    }

    /**
     * Tells whether {@code name} can be used as the simple name of a generated class: a valid identifier that is not
     * one of the restricted identifiers that javac refuses as type names.
     */
    static boolean isValidClassName(String name) {
        return isValidIdentifier(name) && !RESTRICTED_TYPE_NAMES.contains(name);
    }

    /**
     * Tells whether {@code name} would shadow a type the generated code relies on: a public top-level type of
     * {@code java.lang} or one of the imported types.
     */
    static boolean isReservedClassName(String name) {
        if (IMPORTED_TYPE_NAMES.contains(name) || RESTRICTED_TYPE_NAMES.contains(name)) {
            return true;
        }
        try {
            // initialize=false: only the existence and visibility of the type matter, not its static state.
            Class<?> type = Class.forName("java.lang." + name, false, Object.class.getClassLoader());
            return type.getEnclosingClass() == null && Modifier.isPublic(type.getModifiers());
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * Returns the property name Jackson derives from {@code getterName} with its default (legacy) bean naming: strip
     * the {@code get}/{@code is} prefix and lower-case the leading run of upper-case characters.
     */
    static String jacksonPropertyName(String getterName) {
        String base;
        if (getterName.startsWith("get")) {
            base = getterName.substring(3);
        } else if (getterName.startsWith("is")) {
            base = getterName.substring(2);
        } else {
            base = getterName;
        }
        StringBuilder sb = new StringBuilder(base);
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            char lower = Character.toLowerCase(c);
            if (lower == c) {
                break;
            }
            sb.setCharAt(i, lower);
        }
        return sb.toString();
    }

    /** Tells whether {@code name} is a dot-separated sequence of valid identifiers. */
    static boolean isValidPackageName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (String part : name.split("\\.", -1)) {
            if (!isValidIdentifier(part)) {
                return false;
            }
        }
        return true;
    }

    /** Derives a field name from a JSON key: the key itself if valid, else its lowerCamelCase form. */
    static String fieldName(String key) {
        if (SHADOWING_FIELD_NAMES.contains(key)) {
            return key + "_";
        }
        if (isValidIdentifier(key)) {
            return key;
        }
        String name = fixUp(camelCase(key, false));
        return SHADOWING_FIELD_NAMES.contains(name) ? name + "_" : name;
    }

    /**
     * Converts {@code word} to UpperCamelCase.
     *
     * @return the class name, or the empty string if no identifier characters remain
     */
    static String className(String word) {
        String camel = camelCase(word, true);
        if (camel.isEmpty()) {
            return "";
        }
        return fixUp(camel);
    }

    /** Returns {@code name} with its first code point upper-cased. */
    static String capitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        int first = name.codePointAt(0);
        return new StringBuilder()
                .appendCodePoint(Character.toUpperCase(first))
                .append(name, Character.charCount(first), name.length())
                .toString();
    }

    /** Applies the singularization rules to a key taken from under a wildcard. */
    static String singularize(String word) {
        if (word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.endsWith("ses")
                || word.endsWith("xes")
                || word.endsWith("zes")
                || word.endsWith("ches")
                || word.endsWith("shes")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    /** Escapes {@code value} so it can be placed between double quotes in Java source. */
    static String stringLiteral(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    // Octal rather than unicode escapes: javac translates \\uXXXX before lexing, which would
                    // turn an escaped line terminator into a real one.
                    if (c < 0x20 || c == 0x7f) {
                        sb.append(String.format(Locale.ROOT, "\\%03o", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String camelCase(String text, boolean upperFirst) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (Character.isJavaIdentifierPart(cp)) {
                current.appendCodePoint(cp);
            } else if (!current.isEmpty()) {
                parts.add(current.toString());
                current.setLength(0);
            }
        });
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            int first = part.codePointAt(0);
            int converted = (i == 0 && !upperFirst) ? Character.toLowerCase(first) : Character.toUpperCase(first);
            result.appendCodePoint(converted).append(part, Character.charCount(first), part.length());
        }
        return result.toString();
    }

    private static String fixUp(String name) {
        String result = name.isEmpty() ? "_" : name;
        if (!Character.isJavaIdentifierStart(result.codePointAt(0))) {
            result = "_" + result;
        }
        // Also catches a lone "_", which is reserved since Java 9 and would not compile.
        if (SourceVersion.isKeyword(result)) {
            result = result + "_";
        }
        return result;
    }
}
