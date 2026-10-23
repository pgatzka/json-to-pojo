package io.github.pgatzka.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pgatzka.JsonPathLister;
import io.github.pgatzka.JsonType;
import io.github.pgatzka.TypedJsonPath;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Generated;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link PojoGenerator}. Generated sources are compiled with the system Java compiler and inspected via
 * reflection.
 */
class PojoGeneratorTest {

    private static final String PACKAGE = "com.acme.model";

    private static final String GENERATED_VALUE = "io.github.pgatzka.json-pojo-gen";

    private static final String REFERENCE_JSON = """
            {
              "id": 1,
              "name": "widget",
              "price": 9.5,
              "active": true,
              "first name": "x",
              "address": {"city": "Berlin", "zip": "10115"},
              "tags": ["a", "b"],
              "items": [
                {"sku": "s1", "qty": 2, "note": null, "attrs": {"color": "red"}},
                {"sku": "s2", "qty": 2.5, "note": "x", "price": 1.5, "attrs": {"size": 3}}
              ],
              "translations": {"en": {"text": "Hello", "plural": false}, "de": {"text": "Hallo"}},
              "none": null
            }
            """;

    private static final Set<String> REFERENCE_MAP_PATHS = Set.of("$.translations", "$.items[*].attrs");

    private static final List<String> REFERENCE_CLASS_NAMES = List.of("Product", "Address", "Item", "Translation");

    private static final List<String> PRODUCT_FIELDS = List.of(
            "id: long",
            "name: java.lang.String",
            "price: double",
            "active: boolean",
            "firstName: java.lang.String",
            "address: com.acme.model.Address",
            "tags: java.util.List<java.lang.String>",
            "items: java.util.List<com.acme.model.Item>",
            "translations: java.util.Map<java.lang.String, com.acme.model.Translation>",
            "none: java.lang.Object");

    private static final List<String> ADDRESS_FIELDS = List.of("city: java.lang.String", "zip: java.lang.String");

    private static final List<String> ITEM_FIELDS = List.of(
            "sku: java.lang.String",
            "qty: double",
            "note: java.lang.String",
            "attrs: java.util.Map<java.lang.String, java.lang.String>",
            "price: java.lang.Double");

    private static final List<String> TRANSLATION_FIELDS =
            List.of("text: java.lang.String", "plural: java.lang.Boolean");

    private static final String CLASS_NAMING_JSON =
            "{\"addresses\":[{\"a\":1}],\"data\":[{\"b\":1}],\"categories\":[{\"c\":1}],\"boxes\":[{\"d\":1}],"
                    + "\"matrix\":[[{\"e\":1}]]}";

    private static final String COLLISION_JSON =
            "{\"billing\":{\"address\":{\"a\":1}},\"shipping\":{\"address\":{\"b\":2}}}";

    private static final String SHADOWING_JSON = "{\"Objects\":1,\"Double\":2.5,\"java\":\"x\",\"ok\":true}";

    @TempDir
    Path tempDir;

    private final List<URLClassLoader> loaders = new ArrayList<>();

    @AfterEach
    void closeLoaders() throws IOException {
        for (URLClassLoader loader : loaders) {
            loader.close();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static GeneratorOptions options(
            String rootClassName,
            Set<String> mapPaths,
            Map<String, String> classNames,
            boolean equalsAndHashCode,
            boolean toString) {
        return new GeneratorOptions(PACKAGE, rootClassName, mapPaths, classNames, equalsAndHashCode, toString);
    }

    private static List<JavaFile> generateReference(boolean equalsAndHashCode, boolean toString) {
        return PojoGenerator.generate(
                JsonPathLister.listPaths(REFERENCE_JSON, REFERENCE_MAP_PATHS),
                options("Product", REFERENCE_MAP_PATHS, Map.of(), equalsAndHashCode, toString));
    }

    private static List<JavaFile> generateReference() {
        return generateReference(true, true);
    }

    private static List<JavaFile> generate(String json) {
        return generate(json, Set.of(), Map.of());
    }

    private static List<JavaFile> generate(String json, Set<String> mapPaths, Map<String, String> classNames) {
        return PojoGenerator.generate(
                JsonPathLister.listPaths(json, mapPaths), options("Root", mapPaths, classNames, true, true));
    }

    private static List<String> classNamesOf(List<JavaFile> files) {
        return files.stream().map(JavaFile::className).toList();
    }

    private Map<String, Class<?>> compile(List<JavaFile> files) throws IOException, ClassNotFoundException {
        return compile(files, null);
    }

    /** Compiles the files into a fresh directory below {@link #tempDir} and loads every class, keyed by simple name. */
    private Map<String, Class<?>> compile(List<JavaFile> files, Processor processor)
            throws IOException, ClassNotFoundException {
        Path workDir = Files.createTempDirectory(tempDir, "compile");
        Path sourceDir = workDir.resolve("src");
        Path classesDir = workDir.resolve("classes");
        Files.createDirectories(classesDir);

        List<Path> sources = new ArrayList<>();
        for (JavaFile file : files) {
            Path source = sourceDir.resolve(file.relativePath());
            Files.createDirectories(source.getParent());
            Files.writeString(source, file.content(), StandardCharsets.UTF_8);
            sources.add(source);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("system Java compiler").isNotNull();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> compilerOptions = List.of(
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                classesDir.toString(),
                "-encoding",
                "UTF-8",
                processor == null ? "-proc:none" : "-proc:full");
        boolean success;
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            JavaCompiler.CompilationTask task =
                    compiler.getTask(null, fileManager, diagnostics, compilerOptions, null, units);
            if (processor != null) {
                task.setProcessors(List.of(processor));
            }
            success = Boolean.TRUE.equals(task.call());
        }

        List<String> errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(Object::toString)
                .toList();
        assertThat(errors).as("compilation errors").isEmpty();
        assertThat(success).as("compilation succeeded").isTrue();

        URLClassLoader loader =
                new URLClassLoader(new URL[] {classesDir.toUri().toURL()}, PojoGeneratorTest.class.getClassLoader());
        loaders.add(loader);
        Map<String, Class<?>> classes = new LinkedHashMap<>();
        for (JavaFile file : files) {
            classes.put(file.className(), Class.forName(file.packageName() + "." + file.className(), true, loader));
        }
        return classes;
    }

    private static List<Field> declaredFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .toList();
    }

    private static List<String> fieldLines(Class<?> type) {
        return declaredFields(type).stream()
                .map(field -> field.getName() + ": " + field.getGenericType().getTypeName())
                .toList();
    }

    private static Field field(Class<?> type, String name) {
        return declaredFields(type).stream()
                .filter(field -> field.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No field " + name + " in " + type.getName()));
    }

    private static Method getterOf(Class<?> type, Field field) throws NoSuchMethodException {
        String prefix = field.getType() == boolean.class ? "is" : "get";
        return type.getDeclaredMethod(prefix + capitalize(field.getName()));
    }

    private static Method setterOf(Class<?> type, Field field) throws NoSuchMethodException {
        return type.getDeclaredMethod("set" + capitalize(field.getName()), field.getType());
    }

    private static String jsonPropertyValue(Method method) {
        JsonProperty property = method.getAnnotation(JsonProperty.class);
        return property == null ? null : property.value();
    }

    /**
     * Field name to the {@code @JsonProperty} value carried by its accessors, for every field whose accessors are
     * annotated, in declaration order. Asserts that getter and setter carry the same annotation value (or both none).
     */
    private static Map<String, String> accessorJsonPropertyValues(Class<?> type) throws NoSuchMethodException {
        Map<String, String> values = new LinkedHashMap<>();
        for (Field field : declaredFields(type)) {
            Method getter = getterOf(type, field);
            Method setter = setterOf(type, field);
            String getterValue = jsonPropertyValue(getter);
            String setterValue = jsonPropertyValue(setter);
            assertThat(setterValue)
                    .as(
                            "@JsonProperty of %s.%s must match %s.%s",
                            type.getSimpleName(), setter.getName(), type.getSimpleName(), getter.getName())
                    .isEqualTo(getterValue);
            if (getterValue != null) {
                values.put(field.getName(), getterValue);
            }
        }
        return values;
    }

    /** Names of the declared fields annotated {@code @JsonProperty}. */
    private static List<String> annotatedFieldNames(Class<?> type) {
        return declaredFields(type).stream()
                .filter(field -> field.isAnnotationPresent(JsonProperty.class))
                .map(Field::getName)
                .toList();
    }

    /** Names of the declared (non-synthetic) methods annotated {@code @JsonProperty}. */
    private static List<String> annotatedMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> method.isAnnotationPresent(JsonProperty.class))
                .map(Method::getName)
                .toList();
    }

    /** Deserializes {@code json} into {@code type}, serializes it back and asserts the trees are equal. */
    private static void assertSerializationRoundTrip(String json, Class<?> type) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Object value = mapper.readValue(json, type);
        String written = mapper.writeValueAsString(value);
        assertThat(mapper.readTree(written)).as("serialized %s", written).isEqualTo(mapper.readTree(json));
    }

    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Object newInstance(Class<?> type) throws ReflectiveOperationException {
        return type.getConstructor().newInstance();
    }

    private static Object get(Object target, String getter) throws ReflectiveOperationException {
        return target.getClass().getMethod(getter).invoke(target);
    }

    private static void set(Object target, String setter, Class<?> parameterType, Object value)
            throws ReflectiveOperationException {
        target.getClass().getMethod(setter, parameterType).invoke(target, value);
    }

    private static Object address(Class<?> addressClass, String city, String zip) throws ReflectiveOperationException {
        Object address = newInstance(addressClass);
        set(address, "setCity", String.class, city);
        set(address, "setZip", String.class, zip);
        return address;
    }

    /** Asserts a public no-arg constructor and, per field, a public getter and setter with the field's type. */
    private static void assertConstructorAndAccessors(Class<?> type) throws NoSuchMethodException {
        assertThat(Modifier.isPublic(type.getConstructor().getModifiers()))
                .as("public no-arg constructor of %s", type.getSimpleName())
                .isTrue();
        for (Field field : declaredFields(type)) {
            String suffix = capitalize(field.getName());
            String getterName = (field.getType() == boolean.class ? "is" : "get") + suffix;
            Method getter = type.getDeclaredMethod(getterName);
            assertThat(Modifier.isPublic(getter.getModifiers()))
                    .as("%s.%s is public", type.getSimpleName(), getterName)
                    .isTrue();
            assertThat(getter.getGenericReturnType().getTypeName())
                    .as("%s.%s return type", type.getSimpleName(), getterName)
                    .isEqualTo(field.getGenericType().getTypeName());

            String setterName = "set" + suffix;
            Method setter = type.getDeclaredMethod(setterName, field.getType());
            assertThat(Modifier.isPublic(setter.getModifiers()))
                    .as("%s.%s is public", type.getSimpleName(), setterName)
                    .isTrue();
            assertThat(setter.getReturnType()).isEqualTo(void.class);
            assertThat(setter.getGenericParameterTypes()[0].getTypeName())
                    .as("%s.%s parameter type", type.getSimpleName(), setterName)
                    .isEqualTo(field.getGenericType().getTypeName());
        }
    }

    /** Records the {@code @Generated} value of every compiled top-level type (source retention, so read at compile time). */
    private static final class GeneratedAnnotationCollector extends AbstractProcessor {

        private final Set<String> seenTypes = new HashSet<>();
        private final Map<String, List<String>> generatedValues = new HashMap<>();

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (Element element : roundEnv.getRootElements()) {
                if (element instanceof TypeElement type) {
                    String name = type.getQualifiedName().toString();
                    seenTypes.add(name);
                    Generated generated = type.getAnnotation(Generated.class);
                    if (generated != null) {
                        generatedValues.put(name, List.of(generated.value()));
                    }
                }
            }
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Criteria 1-9: reference input
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("1: reference input yields Product, Address, Item, Translation with package and relative path")
    void generate_referenceInput_returnsFourFilesInOrder() {
        List<JavaFile> files = generateReference();

        assertThat(classNamesOf(files)).containsExactlyElementsOf(REFERENCE_CLASS_NAMES);
        for (JavaFile file : files) {
            assertThat(file.packageName()).isEqualTo(PACKAGE);
            assertThat(file.relativePath()).isEqualTo("com/acme/model/" + file.className() + ".java");
        }
    }

    @Test
    @DisplayName("1: generated file contents end with a newline")
    void generate_referenceInput_contentEndsWithNewline() {
        for (JavaFile file : generateReference()) {
            assertThat(file.content()).as(file.className()).endsWith("\n");
        }
    }

    @Test
    @DisplayName("1: returned list is unmodifiable")
    void generate_returnedList_isUnmodifiable() {
        List<JavaFile> files = generateReference();

        assertThatThrownBy(() -> files.add(new JavaFile(PACKAGE, "Extra", "x\n")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> files.remove(0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("2: the four reference files compile without errors")
    void generate_referenceInput_compiles() throws Exception {
        Map<String, Class<?>> classes = compile(generateReference());

        assertThat(classes.keySet()).containsExactlyElementsOf(REFERENCE_CLASS_NAMES);
        for (Class<?> type : classes.values()) {
            assertThat(Modifier.isPublic(type.getModifiers()))
                    .as(type.getName())
                    .isTrue();
            assertThat(type.getPackageName()).isEqualTo(PACKAGE);
        }
    }

    @Test
    @DisplayName("3: compiled reference classes declare exactly the expected fields and generic types in order")
    void generate_referenceInput_declaresExpectedFields() throws Exception {
        Map<String, Class<?>> classes = compile(generateReference());

        assertThat(fieldLines(classes.get("Product"))).containsExactlyElementsOf(PRODUCT_FIELDS);
        assertThat(fieldLines(classes.get("Address"))).containsExactlyElementsOf(ADDRESS_FIELDS);
        assertThat(fieldLines(classes.get("Item"))).containsExactlyElementsOf(ITEM_FIELDS);
        assertThat(fieldLines(classes.get("Translation"))).containsExactlyElementsOf(TRANSLATION_FIELDS);
    }

    @Test
    @DisplayName("3: all fields of the reference classes are private instance fields")
    void generate_referenceInput_fieldsArePrivate() throws Exception {
        Map<String, Class<?>> classes = compile(generateReference());

        for (Class<?> type : classes.values()) {
            for (Field field : declaredFields(type)) {
                assertThat(Modifier.isPrivate(field.getModifiers()))
                        .as("%s.%s is private", type.getSimpleName(), field.getName())
                        .isTrue();
                assertThat(Modifier.isStatic(field.getModifiers()))
                        .as("%s.%s is not static", type.getSimpleName(), field.getName())
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("4: only Product.getFirstName/setFirstName carry @JsonProperty(\"first name\"); no field does")
    void generate_referenceInput_onlyFirstNameAccessorsHaveJsonProperty() throws Exception {
        Map<String, Class<?>> classes = compile(generateReference());
        Class<?> product = classes.get("Product");

        assertThat(jsonPropertyValue(product.getDeclaredMethod("getFirstName"))).isEqualTo("first name");
        assertThat(jsonPropertyValue(product.getDeclaredMethod("setFirstName", String.class)))
                .isEqualTo("first name");

        List<String> annotatedMethods = new ArrayList<>();
        for (Class<?> type : classes.values()) {
            assertThat(annotatedFieldNames(type))
                    .as("fields of %s annotated @JsonProperty", type.getSimpleName())
                    .isEmpty();
            for (String method : annotatedMethodNames(type)) {
                annotatedMethods.add(type.getSimpleName() + "." + method);
            }
        }
        assertThat(annotatedMethods).containsExactlyInAnyOrder("Product.getFirstName", "Product.setFirstName");
    }

    @Test
    @DisplayName("5: every reference class has a public no-arg constructor and typed getters and setters")
    void generate_referenceInput_hasConstructorGettersAndSetters() throws Exception {
        Map<String, Class<?>> classes = compile(generateReference());

        for (Class<?> type : classes.values()) {
            assertConstructorAndAccessors(type);
        }
    }

    @Test
    @DisplayName("5: named accessors from the spec exist with the declared types")
    void generate_referenceInput_namedAccessorsHaveDeclaredTypes() throws Exception {
        Map<String, Class<?>> classes = compile(generateReference());
        Class<?> product = classes.get("Product");
        Class<?> item = classes.get("Item");

        assertThat(product.getMethod("getId").getReturnType()).isEqualTo(long.class);
        assertThat(product.getMethod("setId", long.class).getReturnType()).isEqualTo(void.class);
        assertThat(product.getMethod("isActive").getReturnType()).isEqualTo(boolean.class);
        assertThat(product.getMethod("setActive", boolean.class).getReturnType())
                .isEqualTo(void.class);
        assertThat(product.getMethod("getFirstName").getReturnType()).isEqualTo(String.class);
        assertThat(product.getMethod("setFirstName", String.class).getReturnType())
                .isEqualTo(void.class);
        assertThat(product.getMethod("getPrice").getReturnType()).isEqualTo(double.class);
        assertThat(item.getMethod("getPrice").getReturnType()).isEqualTo(Double.class);
        assertThat(item.getMethod("setPrice", Double.class).getReturnType()).isEqualTo(void.class);
        assertThat(classes.get("Translation").getMethod("getPlural").getReturnType())
                .isEqualTo(Boolean.class);
    }

    @Test
    @DisplayName("5: a primitive boolean field has an is-getter and no get-getter")
    void generate_referenceInput_primitiveBooleanHasNoGetGetter() throws Exception {
        Class<?> product = compile(generateReference()).get("Product");

        assertThatThrownBy(() -> product.getDeclaredMethod("getActive")).isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("6: every reference class is annotated @Generated(\"io.github.pgatzka.json-pojo-gen\")")
    void generate_referenceInput_classesAreAnnotatedGenerated() throws Exception {
        GeneratedAnnotationCollector collector = new GeneratedAnnotationCollector();
        List<JavaFile> files = generateReference();

        compile(files, collector);

        for (JavaFile file : files) {
            String qualifiedName = file.packageName() + "." + file.className();
            assertThat(collector.seenTypes).contains(qualifiedName);
            assertThat(collector.generatedValues.get(qualifiedName))
                    .as("@Generated value of %s", qualifiedName)
                    .containsExactly(GENERATED_VALUE);
        }
    }

    @Test
    @DisplayName("7: Jackson deserializes the reference JSON into the generated classes")
    void generate_referenceInput_jacksonRoundTrip() throws Exception {
        Map<String, Class<?>> classes = compile(generateReference());
        Class<?> productClass = classes.get("Product");

        Object product = new ObjectMapper().readValue(REFERENCE_JSON, productClass);

        assertThat(product).isInstanceOf(productClass);
        assertThat(get(product, "getId")).isEqualTo(1L);
        assertThat(get(product, "getFirstName")).isEqualTo("x");

        List<?> items = (List<?>) get(product, "getItems");
        assertThat(items).hasSize(2);
        assertThat(items.get(0)).isInstanceOf(classes.get("Item"));
        assertThat(get(items.get(1), "getPrice")).isEqualTo(1.5);
        assertThat(get(items.get(0), "getPrice")).isNull();

        Map<?, ?> translations = (Map<?, ?>) get(product, "getTranslations");
        assertThat(translations.get("de")).isInstanceOf(classes.get("Translation"));
        assertThat(get(translations.get("de"), "getPlural")).isNull();
        assertThat(get(translations.get("en"), "getPlural")).isEqualTo(Boolean.FALSE);

        Map<?, ?> attrs = (Map<?, ?>) get(items.get(1), "getAttrs");
        assertThat(attrs.get("size")).isEqualTo("3");
    }

    @Test
    @DisplayName("8: Address instances with equal fields are equal and share a hashCode")
    void generate_equalsAndHashCode_equalFieldsAreEqual() throws Exception {
        Class<?> addressClass = compile(generateReference()).get("Address");

        Object first = address(addressClass, "Berlin", "10115");
        Object second = address(addressClass, "Berlin", "10115");

        assertThat(first.equals(second)).isTrue();
        assertThat(second.equals(first)).isTrue();
        assertThat(first.equals(first)).isTrue();
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    @DisplayName("8: Address with a differing zip is not equal; not equal to null or to an Item")
    void generate_equalsAndHashCode_differentFieldsNullAndOtherTypeAreNotEqual() throws Exception {
        Map<String, Class<?>> classes = compile(generateReference());
        Class<?> addressClass = classes.get("Address");

        Object berlin = address(addressClass, "Berlin", "10115");
        Object otherZip = address(addressClass, "Berlin", "10117");
        Object item = newInstance(classes.get("Item"));

        assertThat(berlin.equals(otherZip)).isFalse();
        assertThat(berlin.equals(null)).isFalse();
        assertThat(berlin.equals(item)).isFalse();
    }

    @Test
    @DisplayName("8: equals compares double, String and null reference fields of Item")
    void generate_equalsAndHashCode_itemComparesDoubleAndReferenceFields() throws Exception {
        Class<?> itemClass = compile(generateReference()).get("Item");

        Object first = newInstance(itemClass);
        set(first, "setSku", String.class, "s1");
        set(first, "setQty", double.class, 2.5);
        Object second = newInstance(itemClass);
        set(second, "setSku", String.class, "s1");
        set(second, "setQty", double.class, 2.5);

        assertThat(first.equals(second)).isTrue();
        assertThat(first.hashCode()).isEqualTo(second.hashCode());

        set(second, "setQty", double.class, 3.0);
        assertThat(first.equals(second)).isFalse();

        set(second, "setQty", double.class, 2.5);
        set(second, "setPrice", Double.class, 1.5);
        assertThat(first.equals(second)).isFalse();
    }

    @Test
    @DisplayName(
            "8: with generateEqualsAndHashCode false no class declares equals or hashCode and Objects is not imported")
    void generate_equalsAndHashCodeDisabled_declaresNeitherMethod() throws Exception {
        List<JavaFile> files = generateReference(false, true);
        Map<String, Class<?>> classes = compile(files);

        for (Class<?> type : classes.values()) {
            assertThatThrownBy(() -> type.getDeclaredMethod("equals", Object.class))
                    .as("%s.equals", type.getSimpleName())
                    .isInstanceOf(NoSuchMethodException.class);
            assertThatThrownBy(() -> type.getDeclaredMethod("hashCode"))
                    .as("%s.hashCode", type.getSimpleName())
                    .isInstanceOf(NoSuchMethodException.class);
        }
        for (JavaFile file : files) {
            assertThat(file.content()).as(file.className()).doesNotContain("import java.util.Objects");
        }
    }

    @Test
    @DisplayName("8: with generateEqualsAndHashCode true every class declares equals and hashCode")
    void generate_equalsAndHashCodeEnabled_declaresBothMethods() throws Exception {
        Map<String, Class<?>> classes = compile(generateReference());

        for (Class<?> type : classes.values()) {
            Method equals = type.getDeclaredMethod("equals", Object.class);
            Method hashCode = type.getDeclaredMethod("hashCode");
            assertThat(equals.getReturnType()).isEqualTo(boolean.class);
            assertThat(Modifier.isPublic(equals.getModifiers())).isTrue();
            assertThat(hashCode.getReturnType()).isEqualTo(int.class);
            assertThat(Modifier.isPublic(hashCode.getModifiers())).isTrue();
        }
    }

    @Test
    @DisplayName("9: Address toString prints Address{city=Berlin, zip=10115}")
    void generate_toString_printsNameAndFields() throws Exception {
        Class<?> addressClass = compile(generateReference()).get("Address");

        assertThat(address(addressClass, "Berlin", "10115").toString()).isEqualTo("Address{city=Berlin, zip=10115}");
    }

    @Test
    @DisplayName("9: with generateToString false no class declares toString")
    void generate_toStringDisabled_declaresNoToString() throws Exception {
        Map<String, Class<?>> classes = compile(generateReference(true, false));

        for (Class<?> type : classes.values()) {
            assertThatThrownBy(() -> type.getDeclaredMethod("toString"))
                    .as("%s.toString", type.getSimpleName())
                    .isInstanceOf(NoSuchMethodException.class);
            assertThat(type.getDeclaredMethod("equals", Object.class)).isNotNull();
        }
    }

    @Test
    @DisplayName("8/9: with both flags false the classes compile and declare none of the three methods")
    void generate_bothFlagsDisabled_declaresNoneOfTheMethods() throws Exception {
        List<JavaFile> files = generateReference(false, false);
        Map<String, Class<?>> classes = compile(files);

        Class<?> addressClass = classes.get("Address");
        assertThatThrownBy(() -> addressClass.getDeclaredMethod("toString")).isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> addressClass.getDeclaredMethod("equals", Object.class))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> addressClass.getDeclaredMethod("hashCode")).isInstanceOf(NoSuchMethodException.class);
        assertThat(fieldLines(addressClass)).containsExactlyElementsOf(ADDRESS_FIELDS);
    }

    @Test
    @DisplayName("Class content: imports are only those used (Address uses no List, Map or JsonProperty)")
    void generate_referenceInput_importsOnlyWhatIsUsed() {
        JavaFile address = generateReference().get(1);

        assertThat(address.className()).isEqualTo("Address");
        assertThat(address.content())
                .doesNotContain("import java.util.List")
                .doesNotContain("import java.util.Map")
                .doesNotContain("import com.fasterxml.jackson.annotation.JsonProperty");
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 10: boxing
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("10: primitives only for plain members; optional, null and generic arguments are boxed")
    void generate_boxing_followsNullableOptionalAndGenericRules() throws Exception {
        List<JavaFile> files = generate("{\"a\":1,\"b\":null,\"c\":[{\"x\":true},{}],\"d\":[1,2]}");
        Map<String, Class<?>> classes = compile(files);

        assertThat(classes.keySet()).containsExactly("Root", "C");
        assertThat(fieldLines(classes.get("Root")))
                .containsExactly(
                        "a: long",
                        "b: java.lang.Object",
                        "c: java.util.List<com.acme.model.C>",
                        "d: java.util.List<java.lang.Long>");
        assertThat(fieldLines(classes.get("C"))).containsExactly("x: java.lang.Boolean");
        assertThat(classes.get("C").getMethod("getX").getReturnType()).isEqualTo(Boolean.class);
        assertConstructorAndAccessors(classes.get("Root"));
        assertConstructorAndAccessors(classes.get("C"));
    }

    @Test
    @DisplayName("10: nullable integer and float members are boxed to Long and Double")
    void generate_boxing_nullableNumbersAreBoxed() throws Exception {
        Map<String, Class<?>> classes =
                compile(generate("{\"l\":[{\"n\":1,\"f\":1.5,\"k\":2},{\"n\":null,\"f\":null,\"k\":3}]}"));

        assertThat(fieldLines(classes.get("L"))).containsExactly("n: java.lang.Long", "f: java.lang.Double", "k: long");
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 11: field naming
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName(
            "11: invalid keys become camelCase fields with @JsonProperty on their accessors, valid keys stay as is")
    void generate_fieldNaming_convertsInvalidKeys() throws Exception {
        Map<String, Class<?>> classes =
                compile(generate("{\"first name\":1,\"zip-code\":2,\"class\":3,\"1st\":4,\"ä\":5,\"_ok9\":6}"));
        Class<?> root = classes.get("Root");

        assertThat(declaredFields(root))
                .extracting(Field::getName)
                .containsExactly("firstName", "zipCode", "class_", "_1st", "ä", "_ok9");

        assertThat(annotatedFieldNames(root))
                .as("fields annotated @JsonProperty")
                .isEmpty();
        assertThat(annotatedMethodNames(root))
                .containsExactlyInAnyOrder(
                        "getFirstName", "setFirstName",
                        "getZipCode", "setZipCode",
                        "getClass_", "setClass_",
                        "get_1st", "set_1st");
        assertThat(accessorJsonPropertyValues(root))
                .containsExactly(
                        Map.entry("firstName", "first name"),
                        Map.entry("zipCode", "zip-code"),
                        Map.entry("class_", "class"),
                        Map.entry("_1st", "1st"));

        for (String getter : List.of("getFirstName", "getZipCode", "getClass_", "get_1st", "getÄ", "get_ok9")) {
            assertThat(root.getDeclaredMethod(getter).getReturnType())
                    .as(getter)
                    .isEqualTo(long.class);
        }
        assertConstructorAndAccessors(root);
    }

    @Test
    @DisplayName("11: multi-part key 'a b c' becomes aBC")
    void generate_fieldNaming_multiPartKey() throws Exception {
        Class<?> root = compile(generate("{\"a b c\":1}")).get("Root");

        assertThat(declaredFields(root)).extracting(Field::getName).containsExactly("aBC");
        assertThat(annotatedFieldNames(root)).isEmpty();
        assertThat(accessorJsonPropertyValues(root)).containsExactly(Map.entry("aBC", "a b c"));
    }

    @Test
    @DisplayName("11: @JsonProperty values with quotes, backslashes and apostrophes are escaped correctly")
    void generate_fieldNaming_escapesJsonPropertyValues() throws Exception {
        Class<?> root = compile(generate("{\"say \\\"hi\\\"\":1,\"back\\\\slash\":2,\"it's\":3}"))
                .get("Root");

        assertThat(declaredFields(root)).extracting(Field::getName).containsExactly("sayHi", "backSlash", "itS");
        assertThat(annotatedFieldNames(root)).isEmpty();
        assertThat(accessorJsonPropertyValues(root))
                .containsExactly(
                        Map.entry("sayHi", "say \"hi\""),
                        Map.entry("backSlash", "back\\slash"),
                        Map.entry("itS", "it's"));
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 18: @JsonProperty follows Jackson's getter-derived property name
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("18: valid keys Id, URL, aB stay as field names but their accessors carry @JsonProperty; ok's do not")
    void generate_jacksonNaming_annotatesKeysWhoseGetterNameDiffers() throws Exception {
        Class<?> root =
                compile(generate("{\"Id\":1,\"URL\":\"u\",\"aB\":2,\"ok\":3}")).get("Root");

        assertThat(fieldLines(root)).containsExactly("Id: long", "URL: java.lang.String", "aB: long", "ok: long");
        assertThat(annotatedFieldNames(root))
                .as("fields annotated @JsonProperty")
                .isEmpty();
        assertThat(accessorJsonPropertyValues(root))
                .containsExactly(Map.entry("Id", "Id"), Map.entry("URL", "URL"), Map.entry("aB", "aB"));
        assertThat(annotatedMethodNames(root))
                .containsExactlyInAnyOrder("getId", "setId", "getURL", "setURL", "getAB", "setAB");
        assertThat(root.getDeclaredMethod("getId")
                        .getAnnotation(JsonProperty.class)
                        .value())
                .isEqualTo("Id");
        assertThat(root.getDeclaredMethod("setId", long.class)
                        .getAnnotation(JsonProperty.class)
                        .value())
                .isEqualTo("Id");
        assertThat(root.getDeclaredMethod("getOk").getAnnotation(JsonProperty.class))
                .isNull();
        assertThat(root.getDeclaredMethod("setOk", long.class).getAnnotation(JsonProperty.class))
                .isNull();
        assertThat(root.getDeclaredMethod("getId").getReturnType()).isEqualTo(long.class);
        assertThat(root.getDeclaredMethod("getURL").getReturnType()).isEqualTo(String.class);
        assertThat(root.getDeclaredMethod("getAB").getReturnType()).isEqualTo(long.class);
        assertThat(root.getDeclaredMethod("getOk").getReturnType()).isEqualTo(long.class);
        assertConstructorAndAccessors(root);
    }

    @Test
    @DisplayName("18: Jackson deserializes keys Id, URL, aB, ok into the generated class")
    void generate_jacksonNaming_jacksonRoundTrip() throws Exception {
        Class<?> rootClass =
                compile(generate("{\"Id\":1,\"URL\":\"u\",\"aB\":2,\"ok\":3}")).get("Root");

        Object root = new ObjectMapper().readValue("{\"Id\":1,\"URL\":\"u\",\"aB\":2,\"ok\":3}", rootClass);

        assertThat(get(root, "getId")).isEqualTo(1L);
        assertThat(get(root, "getURL")).isEqualTo("u");
        assertThat(get(root, "getAB")).isEqualTo(2L);
        assertThat(get(root, "getOk")).isEqualTo(3L);
    }

    @Test
    @DisplayName("18: serialization round trip of Id, URL, aB, ok yields an equal tree without duplicate properties")
    void generate_jacksonNaming_serializationRoundTrip() throws Exception {
        String json = "{\"Id\":1,\"URL\":\"u\",\"aB\":2,\"ok\":3}";
        Class<?> rootClass = compile(generate(json)).get("Root");

        ObjectMapper mapper = new ObjectMapper();
        String written = mapper.writeValueAsString(mapper.readValue(json, rootClass));

        assertThat(mapper.readTree(written)).as("serialized %s", written).isEqualTo(mapper.readTree(json));
        assertThat(mapper.readTree(written).has("id"))
                .as("no duplicate id property")
                .isFalse();
        assertThat(mapper.readTree(written).size()).isEqualTo(4);
    }

    @Test
    @DisplayName("18: URLValue is annotated; lower-camel keys whose getter name matches are not")
    void generate_jacksonNaming_leadingUpperCaseRunIsLowerCased() throws Exception {
        String json = "{\"URLValue\":1,\"firstName\":2,\"zipCode\":3}";
        Class<?> rootClass = compile(generate(json)).get("Root");

        assertThat(declaredFields(rootClass))
                .extracting(Field::getName)
                .containsExactly("URLValue", "firstName", "zipCode");
        assertThat(annotatedFieldNames(rootClass)).isEmpty();
        assertThat(accessorJsonPropertyValues(rootClass)).containsExactly(Map.entry("URLValue", "URLValue"));

        Object root = new ObjectMapper().readValue(json, rootClass);
        assertThat(get(root, "getURLValue")).isEqualTo(1L);
        assertThat(get(root, "getFirstName")).isEqualTo(2L);
        assertThat(get(root, "getZipCode")).isEqualTo(3L);
        assertSerializationRoundTrip(json, rootClass);
    }

    @Test
    @DisplayName("11/18: Jackson deserializes the field-naming input via the @JsonProperty keys")
    void generate_fieldNaming_jacksonRoundTrip() throws Exception {
        String json = "{\"first name\":1,\"zip-code\":2,\"class\":3,\"1st\":4,\"ä\":5,\"_ok9\":6}";
        Class<?> rootClass = compile(generate(json)).get("Root");

        Object root = new ObjectMapper().readValue(json, rootClass);

        assertThat(get(root, "getFirstName")).isEqualTo(1L);
        assertThat(get(root, "getZipCode")).isEqualTo(2L);
        assertThat(get(root, "getClass_")).isEqualTo(3L);
        assertThat(get(root, "get_1st")).isEqualTo(4L);
        assertThat(get(root, "getÄ")).isEqualTo(5L);
        assertThat(get(root, "get_ok9")).isEqualTo(6L);
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 21: shadowing names Objects, Double, java
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("21: keys Objects, Double, java get _ appended and annotated accessors; ok stays as is")
    void generate_shadowingNames_getUnderscoreAppended() throws Exception {
        List<JavaFile> files = generate(SHADOWING_JSON);
        Class<?> root = compile(files).get("Root");

        assertThat(fieldLines(root))
                .containsExactly("Objects_: long", "Double_: double", "java_: java.lang.String", "ok: boolean");
        assertThat(annotatedFieldNames(root))
                .as("fields annotated @JsonProperty")
                .isEmpty();
        assertThat(accessorJsonPropertyValues(root))
                .containsExactly(
                        Map.entry("Objects_", "Objects"), Map.entry("Double_", "Double"), Map.entry("java_", "java"));
        assertThat(annotatedMethodNames(root))
                .containsExactlyInAnyOrder(
                        "getObjects_", "setObjects_", "getDouble_", "setDouble_", "getJava_", "setJava_");
        assertThat(root.getDeclaredMethod("isOk").getReturnType()).isEqualTo(boolean.class);
        assertConstructorAndAccessors(root);
    }

    @Test
    @DisplayName("21: the shadowing-names class compiles with both flags on and declares equals, hashCode, toString")
    void generate_shadowingNames_compilesWithEqualsHashCodeAndToString() throws Exception {
        Class<?> rootClass = compile(generate(SHADOWING_JSON)).get("Root");

        assertThat(rootClass.getDeclaredMethod("equals", Object.class).getReturnType())
                .isEqualTo(boolean.class);
        assertThat(rootClass.getDeclaredMethod("hashCode").getReturnType()).isEqualTo(int.class);
        assertThat(rootClass.getDeclaredMethod("toString").getReturnType()).isEqualTo(String.class);

        ObjectMapper mapper = new ObjectMapper();
        Object first = mapper.readValue(SHADOWING_JSON, rootClass);
        Object second = mapper.readValue(SHADOWING_JSON, rootClass);
        assertThat(first.equals(second)).isTrue();
        assertThat(first.hashCode()).isEqualTo(second.hashCode());

        set(second, "setDouble_", double.class, 3.5);
        assertThat(first.equals(second)).isFalse();
    }

    @Test
    @DisplayName("21: readValue then writeValueAsString of the shadowing-names document round-trips to an equal tree")
    void generate_shadowingNames_serializationRoundTrip() throws Exception {
        Class<?> rootClass = compile(generate(SHADOWING_JSON)).get("Root");

        Object root = new ObjectMapper().readValue(SHADOWING_JSON, rootClass);
        assertThat(get(root, "getObjects_")).isEqualTo(1L);
        assertThat(get(root, "getDouble_")).isEqualTo(2.5);
        assertThat(get(root, "getJava_")).isEqualTo("x");
        assertThat(get(root, "isOk")).isEqualTo(true);

        assertSerializationRoundTrip(SHADOWING_JSON, rootClass);
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 19: reserved class names
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("19: objects[*] derives Object, which is reserved")
    void generate_reservedDerivedName_object_throws() {
        assertThatThrownBy(() -> generate("{\"objects\":[{\"a\":1}]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Class name Object is reserved");
    }

    @Test
    @DisplayName("19: strings[*] derives String, which is reserved")
    void generate_reservedDerivedName_string_throws() {
        assertThatThrownBy(() -> generate("{\"strings\":[{\"a\":1}]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Class name String is reserved");
    }

    @Test
    @DisplayName("19: lists[*] derives List, which is reserved")
    void generate_reservedDerivedName_list_throws() {
        assertThatThrownBy(() -> generate("{\"lists\":[{\"a\":1}]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Class name List is reserved");
    }

    @Test
    @DisplayName("19: other derived java.lang names and generated-code import names are reserved")
    void generate_reservedDerivedNames_otherNames_throw() {
        Map<String, String> jsonToReservedName = new LinkedHashMap<>();
        jsonToReservedName.put("{\"maps\":[{\"a\":1}]}", "Map");
        jsonToReservedName.put("{\"records\":[{\"a\":1}]}", "Record");
        jsonToReservedName.put("{\"threads\":[{\"a\":1}]}", "Thread");
        jsonToReservedName.put("{\"longs\":[{\"a\":1}]}", "Long");
        jsonToReservedName.put("{\"overrides\":[{\"a\":1}]}", "Override");
        jsonToReservedName.put("{\"objects\":{\"a\":1}}", "Objects");
        jsonToReservedName.put("{\"generated\":{\"a\":1}}", "Generated");
        jsonToReservedName.put("{\"jsonProperty\":{\"a\":1}}", "JsonProperty");

        jsonToReservedName.forEach((json, name) -> assertThatThrownBy(() -> generate(json))
                .as(json)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Class name " + name + " is reserved"));
    }

    @Test
    @DisplayName("19: a reserved name in a nested object is detected")
    void generate_reservedDerivedName_nested_throws() {
        assertThatThrownBy(() -> generate("{\"outer\":{\"strings\":[{\"a\":1}]}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Class name String is reserved");
    }

    @Test
    @DisplayName("19: classNames resolves a reserved derived name: objects[*] becomes Thing")
    void generate_reservedDerivedName_resolvedByClassNames() throws Exception {
        List<JavaFile> files = generate("{\"objects\":[{\"a\":1}]}", Set.of(), Map.of("$.objects[*]", "Thing"));

        assertThat(classNamesOf(files)).containsExactly("Root", "Thing");
        Map<String, Class<?>> classes = compile(files);
        assertThat(fieldLines(classes.get("Root"))).containsExactly("objects: java.util.List<com.acme.model.Thing>");
        assertThat(fieldLines(classes.get("Thing"))).containsExactly("a: long");
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 20: accessor collisions
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("20: keys a and A both produce getA")
    void generate_accessorCollision_throws() {
        assertThatThrownBy(() -> generate("{\"a\":1,\"A\":2}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Accessor name getA used by both");
    }

    @Test
    @DisplayName("20: keys aB and AB both produce getAB")
    void generate_accessorCollision_multiCharacter_throws() {
        assertThatThrownBy(() -> generate("{\"aB\":1,\"AB\":2}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Accessor name getAB used by both");
    }

    @Test
    @DisplayName("20: an accessor collision inside a nested object is detected")
    void generate_accessorCollision_nested_throws() {
        assertThatThrownBy(() -> generate("{\"o\":{\"x\":\"s\",\"X\":\"t\"}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Accessor name getX used by both");
    }

    @Test
    @DisplayName("20: key Class would produce getClass() and conflicts with Object.getClass()")
    void generate_fieldNamedClass_conflictsWithGetClass() {
        assertThatThrownBy(() -> generate("{\"Class\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Field name Class conflicts with Object.getClass()");
    }

    @Test
    @DisplayName("20: key Class inside a nested object conflicts with Object.getClass()")
    void generate_fieldNamedClass_nested_conflictsWithGetClass() {
        assertThatThrownBy(() -> generate("{\"items\":[{\"Class\":\"x\"}]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Field name Class conflicts with Object.getClass()");
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 12: class naming
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("12: class names are singularized keys in first-occurrence order")
    void generate_classNaming_singularizesArrayKeys() {
        assertThat(classNamesOf(generate(CLASS_NAMING_JSON)))
                .containsExactly("Root", "Address", "Data", "Category", "Box", "Matrix");
    }

    @Test
    @DisplayName("12: class-naming input compiles and references the derived classes")
    void generate_classNaming_compilesWithDerivedTypes() throws Exception {
        Map<String, Class<?>> classes = compile(generate(CLASS_NAMING_JSON));

        assertThat(fieldLines(classes.get("Root")))
                .containsExactly(
                        "addresses: java.util.List<com.acme.model.Address>",
                        "data: java.util.List<com.acme.model.Data>",
                        "categories: java.util.List<com.acme.model.Category>",
                        "boxes: java.util.List<com.acme.model.Box>",
                        "matrix: java.util.List<java.util.List<com.acme.model.Matrix>>");
        assertThat(fieldLines(classes.get("Matrix"))).containsExactly("e: long");
    }

    @Test
    @DisplayName("12: singularization rules for ches, shes, zes, ss and s")
    void generate_classNaming_singularizationRules() {
        assertThat(classNamesOf(generate("{\"matches\":[{\"a\":1}],\"wishes\":[{\"b\":1}],\"quizzes\":[{\"c\":1}],"
                        + "\"glass\":[{\"d\":1}],\"status\":[{\"e\":1}]}")))
                .containsExactly("Root", "Match", "Wish", "Quizz", "Glass", "Statu");
    }

    @Test
    @DisplayName("12: keys of plain object members are not singularized")
    void generate_classNaming_plainObjectKeyIsNotSingularized() {
        assertThat(classNamesOf(generate("{\"addresses\":{\"a\":1}}"))).containsExactly("Root", "Addresses");
    }

    @Test
    @DisplayName("12: a multi-word key becomes UpperCamelCase after singularization")
    void generate_classNaming_multiWordKeyBecomesUpperCamelCase() {
        assertThat(classNamesOf(generate("{\"line items\":[{\"a\":1}]}"))).containsExactly("Root", "LineItem");
    }

    @Test
    @DisplayName("12: a key that yields an empty class name cannot be converted")
    void generate_classNaming_emptyDerivedName_throws() {
        List<TypedJsonPath> paths = JsonPathLister.listPaths("{\"-\":{\"a\":1}}");

        assertThatThrownBy(() -> PojoGenerator.generate(paths, GeneratorOptions.defaults(PACKAGE, "Root")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Cannot derive class name for")
                .hasMessageContaining("$['-']");
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 13: classNames override
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("13: classNames override renames $.data[*] to Datum")
    void generate_classNamesOverride_renamesClass() {
        assertThat(classNamesOf(generate(CLASS_NAMING_JSON, Set.of(), Map.of("$.data[*]", "Datum"))))
                .containsExactly("Root", "Address", "Datum", "Category", "Box", "Matrix");
    }

    @Test
    @DisplayName("13: classNames override is used in referencing field types")
    void generate_classNamesOverride_usedInFieldTypes() throws Exception {
        Map<String, Class<?>> classes = compile(generate(CLASS_NAMING_JSON, Set.of(), Map.of("$.data[*]", "Datum")));

        assertThat(field(classes.get("Root"), "data").getGenericType().getTypeName())
                .isEqualTo("java.util.List<com.acme.model.Datum>");
    }

    @Test
    @DisplayName("13: classNames override for the root takes precedence over rootClassName")
    void generate_classNamesOverride_root() {
        assertThat(classNamesOf(generate("{\"a\":{\"x\":1}}", Set.of(), Map.of("$", "Top"))))
                .containsExactly("Top", "A");
    }

    @Test
    @DisplayName("13: classNames key that does not exist fails")
    void generate_classNamesOverride_unknownPath_throws() {
        assertThatThrownBy(() -> generate(CLASS_NAMING_JSON, Set.of(), Map.of("$.nope", "X")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("classNames path not found");
    }

    @Test
    @DisplayName("13: classNames key that points to an array (not an object path) fails")
    void generate_classNamesOverride_nonObjectPath_throws() {
        assertThatThrownBy(() -> generate(CLASS_NAMING_JSON, Set.of(), Map.of("$.addresses", "X")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("classNames path not found");
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 14: collisions
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("14: two object paths resolving to Address collide")
    void generate_classNameCollision_throws() {
        assertThatThrownBy(() -> generate(COLLISION_JSON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Class name Address used by both")
                .hasMessageContaining("$.billing.address")
                .hasMessageContaining("$.shipping.address");
    }

    @Test
    @DisplayName("14: classNames resolves the collision")
    void generate_classNameCollision_resolvedByClassNames() throws Exception {
        List<JavaFile> files = generate(COLLISION_JSON, Set.of(), Map.of("$.shipping.address", "ShippingAddress"));

        assertThat(classNamesOf(files)).containsExactly("Root", "Billing", "Address", "Shipping", "ShippingAddress");

        Map<String, Class<?>> classes = compile(files);
        assertThat(fieldLines(classes.get("Billing"))).containsExactly("address: com.acme.model.Address");
        assertThat(fieldLines(classes.get("Shipping"))).containsExactly("address: com.acme.model.ShippingAddress");
        assertThat(fieldLines(classes.get("ShippingAddress"))).containsExactly("b: long");
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 15: maps and empty arrays
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("15: an empty map becomes Map<String, Object>")
    void generate_emptyMap_isMapOfObject() throws Exception {
        List<JavaFile> files = generate("{\"m\":{}}", Set.of("$.m"), Map.of());

        assertThat(classNamesOf(files)).containsExactly("Root");
        assertThat(fieldLines(compile(files).get("Root")))
                .containsExactly("m: java.util.Map<java.lang.String, java.lang.Object>");
    }

    @Test
    @DisplayName("15: a map of objects becomes Map<String, M> with a class M")
    void generate_mapOfObjects_isMapOfValueClass() throws Exception {
        List<JavaFile> files = generate("{\"m\":{\"a\":{\"x\":1}}}", Set.of("$.m"), Map.of());

        assertThat(classNamesOf(files)).containsExactly("Root", "M");
        Map<String, Class<?>> classes = compile(files);
        assertThat(fieldLines(classes.get("Root")))
                .containsExactly("m: java.util.Map<java.lang.String, com.acme.model.M>");
        assertThat(fieldLines(classes.get("M"))).containsExactly("x: long");
    }

    @Test
    @DisplayName("15: an empty array becomes List<Object>")
    void generate_emptyArray_isListOfObject() throws Exception {
        assertThat(fieldLines(compile(generate("{\"e\":[]}")).get("Root")))
                .containsExactly("e: java.util.List<java.lang.Object>");
    }

    @Test
    @DisplayName("15: a map of numbers becomes Map<String, Long>")
    void generate_mapOfIntegers_isMapOfLong() throws Exception {
        assertThat(fieldLines(compile(generate("{\"m\":{\"a\":1,\"b\":2}}", Set.of("$.m"), Map.of()))
                        .get("Root")))
                .containsExactly("m: java.util.Map<java.lang.String, java.lang.Long>");
    }

    // ---------------------------------------------------------------------------------------------
    // Criterion 16: empty root
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("16: empty root yields one compilable empty Root class with toString Root{} and equal instances")
    void generate_emptyRoot_yieldsEmptyRootClass() throws Exception {
        List<JavaFile> files = generate("{}");

        assertThat(classNamesOf(files)).containsExactly("Root");
        Class<?> root = compile(files).get("Root");
        assertThat(declaredFields(root)).isEmpty();

        Object first = newInstance(root);
        Object second = newInstance(root);
        assertThat(first.toString()).isEqualTo("Root{}");
        assertThat(first.equals(second)).isTrue();
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    @DisplayName("16: an empty path list yields one empty root class")
    void generate_emptyPathList_yieldsRootClass() {
        List<JavaFile> files = PojoGenerator.generate(List.of(), GeneratorOptions.defaults(PACKAGE, "Root"));

        assertThat(classNamesOf(files)).containsExactly("Root");
        assertThat(files.get(0).packageName()).isEqualTo(PACKAGE);
    }

    // ---------------------------------------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Edge: PojoGenerator has no public constructor")
    void pojoGenerator_hasNoPublicConstructor() {
        assertThat(PojoGenerator.class.getConstructors()).isEmpty();
    }

    @Test
    @DisplayName("Edge: null paths throws NullPointerException")
    void generate_nullPaths_throwsNullPointerException() {
        assertThatThrownBy(() -> PojoGenerator.generate(null, GeneratorOptions.defaults(PACKAGE, "Root")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: null options throws NullPointerException")
    void generate_nullOptions_throwsNullPointerException() {
        List<TypedJsonPath> paths = JsonPathLister.listPaths("{\"a\":1}");

        assertThatThrownBy(() -> PojoGenerator.generate(paths, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Edge: root array is rejected")
    void generate_rootArray_throws() {
        List<TypedJsonPath> paths = JsonPathLister.listPaths("[{\"a\":1}]");

        assertThatThrownBy(() -> PojoGenerator.generate(paths, GeneratorOptions.defaults(PACKAGE, "Root")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Root must be a JSON object");
    }

    @Test
    @DisplayName("Edge: colliding field names fail")
    void generate_fieldNameCollision_throws() {
        assertThatThrownBy(() -> generate("{\"a b\":1,\"a-b\":2}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Field name aB used by both");
    }

    @Test
    @DisplayName("Edge: malformed paths foo, $.a..b, $['x, $.a[0] each fail with Invalid path")
    void generate_malformedPath_throwsInvalidPath() {
        for (String malformed : List.of("foo", "$.a..b", "$['x", "$.a[0]")) {
            List<TypedJsonPath> paths = List.of(new TypedJsonPath(malformed, JsonType.STRING));

            assertThatThrownBy(() -> PojoGenerator.generate(paths, GeneratorOptions.defaults(PACKAGE, "Root")))
                    .as(malformed)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Invalid path");
        }
    }

    @Test
    @DisplayName("Edge: a malformed path after valid paths fails with Invalid path")
    void generate_malformedPathAfterValidPath_throwsInvalidPath() {
        List<TypedJsonPath> paths =
                List.of(new TypedJsonPath("$.a", JsonType.INTEGER), new TypedJsonPath("bar", JsonType.STRING));

        assertThatThrownBy(() -> PojoGenerator.generate(paths, GeneratorOptions.defaults(PACKAGE, "Root")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid path");
    }

    @Test
    @DisplayName("Edge: an object class name colliding with the root class name fails")
    void generate_rootClassNameCollision_throws() {
        assertThatThrownBy(() -> generate("{\"root\":{\"a\":1}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Class name Root used by both")
                .hasMessageContaining("$.root");
    }

    @Test
    @DisplayName("Edge: empty key at the root is rejected with Empty JSON key at $")
    void generate_emptyKeyAtRoot_throws() {
        assertThatThrownBy(() -> generate("{\"\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Empty JSON key at $");
    }

    @Test
    @DisplayName("Edge: empty key in a nested object is rejected with Empty JSON key at $.a")
    void generate_emptyKeyInNestedObject_throws() {
        assertThatThrownBy(() -> generate("{\"a\":{\"\":1}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Empty JSON key at $.a");
    }

    @Test
    @DisplayName("Edge: mapPaths containing $ is rejected because the root must be an object")
    void generate_rootAsMapPath_throws() {
        Set<String> mapPaths = Set.of("$");
        List<TypedJsonPath> paths = JsonPathLister.listPaths("{\"a\":{\"x\":1}}", mapPaths);

        assertThatThrownBy(() -> PojoGenerator.generate(paths, options("Root", mapPaths, Map.of(), true, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Root must be a JSON object, not a map path");
    }

    @Test
    @DisplayName("Edge: class names differing only in case (Item / ITEM) collide")
    void generate_caseOnlyClassNameCollision_throws() {
        assertThatThrownBy(() -> generate("{\"item\":{\"a\":1},\"ITEM\":{\"b\":1}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Class name")
                .hasMessageContaining("collides with")
                .hasMessageContaining("$.item")
                .hasMessageContaining("$.ITEM");
    }

    @Test
    @DisplayName("Edge: key --- yields field __ with get__/set__ annotated @JsonProperty(\"---\") and compiles")
    void generate_emptyCamelCaseResult_becomesDoubleUnderscore() throws Exception {
        Class<?> root = compile(generate("{\"---\":1}")).get("Root");

        assertThat(fieldLines(root)).containsExactly("__: long");
        assertThat(annotatedFieldNames(root)).isEmpty();
        assertThat(jsonPropertyValue(root.getDeclaredMethod("get__"))).isEqualTo("---");
        assertThat(jsonPropertyValue(root.getDeclaredMethod("set__", long.class)))
                .isEqualTo("---");
        assertThat(annotatedMethodNames(root)).containsExactlyInAnyOrder("get__", "set__");
        assertConstructorAndAccessors(root);
    }

    @Test
    @DisplayName("Edge: an array of nulls becomes List<Object>")
    void generate_arrayOfNulls_isListOfObject() throws Exception {
        assertThat(fieldLines(compile(generate("{\"x\":[null]}")).get("Root")))
                .containsExactly("x: java.util.List<java.lang.Object>");
    }

    @Test
    @DisplayName("Edge: nested arrays become List<List<Long>>")
    void generate_nestedArrays_isNestedList() throws Exception {
        assertThat(fieldLines(compile(generate("{\"matrix\":[[1]]}")).get("Root")))
                .containsExactly("matrix: java.util.List<java.util.List<java.lang.Long>>");
    }
}
