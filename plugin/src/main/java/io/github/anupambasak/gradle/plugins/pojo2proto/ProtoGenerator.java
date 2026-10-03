/*
 * Copyright 2026 the project's contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.anupambasak.gradle.plugins.pojo2proto;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.type.TypeParameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProtoGenerator {

    /**
     * Generation settings. Defaults reproduce the original behaviour: one shared proto package, no name suffix.
     */
    public static final class Options {
        private boolean prefixEnumNames;
        private String nameSuffix = "";
        private boolean usePojoPackages;
        private String sharedPackage = "";
        private String javaPackageSuffix = "";
        private boolean optionalFields;

        /** Prefix enum values with the enum name in UPPER_SNAKE_CASE. */
        public Options prefixEnumNames(boolean prefixEnumNames) {
            this.prefixEnumNames = prefixEnumNames;
            return this;
        }

        /**
         * Appended to the name of every generated top-level message and enum (and so to its file name):
         * {@code Address -> AddressProto} in {@code AddressProto.proto}.
         */
        public Options nameSuffix(String nameSuffix) {
            this.nameSuffix = nameSuffix == null ? "" : nameSuffix.trim();
            return this;
        }

        /**
         * Use each POJO's Java package as the proto {@code package} and write its file into the matching
         * directory ({@code com.x.dtos.Address -> com/x/dtos/Address.proto}).
         */
        public Options usePojoPackages(boolean usePojoPackages) {
            this.usePojoPackages = usePojoPackages;
            return this;
        }

        /**
         * The proto package shared by all files when {@link #usePojoPackages} is off; ignored with it. The Gradle
         * task passes {@code packageName}, which defaults to the project's group.
         */
        public Options sharedPackage(String sharedPackage) {
            this.sharedPackage = sharedPackage == null ? "" : sharedPackage.trim();
            return this;
        }

        /**
         * Appended to the Java package to form {@code option java_package}, e.g. {@code ".proto"}. With
         * {@link #usePojoPackages} it is part of the proto package and directory as well
         * ({@code com.x.dtos.Address -> com/x/dtos/proto/AddressProto.proto}, {@code package com.x.dtos.proto}),
         * so file location, proto package and java_package all match. With the shared package it only affects
         * {@code java_package}.
         */
        public Options javaPackageSuffix(String javaPackageSuffix) {
            this.javaPackageSuffix = javaPackageSuffix == null ? "" : javaPackageSuffix.trim();
            return this;
        }

        /**
         * Mark every singular field {@code optional} (proto3 explicit presence), so an unset field can be told apart
         * from one set to its default ({@code 0}, {@code false}, {@code ""}, first enum value) via {@code hasX()} and
         * {@code clearX()}. {@code repeated} and {@code map} fields cannot be optional in protobuf and are left as is.
         */
        public Options optionalFields(boolean optionalFields) {
            this.optionalFields = optionalFields;
            return this;
        }
    }

    private final boolean prefixEnumNames;
    private final String nameSuffix;
    private final boolean usePojoPackages;
    private final String sharedPackage;
    private final String javaPackageSuffix;
    private final boolean optionalFields;

    /** Proto names of the top-level types registered with {@link #registerTypes}, keyed by declaration identity. */
    private final Map<TypeDeclaration<?>, String> protoNames = new IdentityHashMap<>();
    /** Same as {@link #protoNames} but without {@link Options#nameSuffix}; used for enum value prefixes. */
    private final Map<TypeDeclaration<?>, String> baseNames = new IdentityHashMap<>();
    private final List<TypeDeclaration<?>> topLevelTypes = new ArrayList<>();

    public ProtoGenerator() {
        this(false);
    }

    public ProtoGenerator(boolean prefixEnumNames) {
        this(new Options().prefixEnumNames(prefixEnumNames));
    }

    public ProtoGenerator(Options options) {
        this.prefixEnumNames = options.prefixEnumNames;
        this.nameSuffix = options.nameSuffix;
        this.usePojoPackages = options.usePojoPackages;
        this.sharedPackage = options.sharedPackage;
        this.javaPackageSuffix = options.javaPackageSuffix;
        this.optionalFields = options.optionalFields;
    }

    /**
     * Registers every top-level type that will be generated and assigns its proto name.
     * <p>
     * All generated types share one proto package, and generated file names and Java classes clash on
     * case-insensitive file systems (Windows, macOS). So when two types have names that differ only in case
     * ({@code FareDetailDTO} / {@code FareDetailDto}) or are equal ({@code Status} in two packages), each of
     * them is renamed by prefixing its Java package's last segment in PascalCase
     * ({@code com.x.pricing.FareDetailDTO -> PricingFareDetailDTO}). More segments are used if that is still
     * ambiguous. Types without a clash keep their name. References, imports and file names follow the new names.
     *
     * @return renamed types, fully qualified Java name to proto name
     */
    public Map<String, String> registerTypes(List<CompilationUnit> cus) {
        protoNames.clear();
        baseNames.clear();
        topLevelTypes.clear();
        topLevelWrappers.clear();
        nestedWrappers.clear();
        for (CompilationUnit cu : cus) {
            topLevelTypes.addAll(cu.getTypes());
        }
        Map<String, List<TypeDeclaration<?>>> byKey = new TreeMap<>();
        for (TypeDeclaration<?> type : topLevelTypes) {
            // With usePojoPackages only types sharing a proto package (and so a directory) can clash
            String scope = usePojoPackages ? protoPackage(type) + "|" : "";
            byKey.computeIfAbsent(scope + type.getNameAsString().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(type);
        }
        Map<String, Set<String>> takenByPackage = new HashMap<>();
        for (List<TypeDeclaration<?>> group : byKey.values()) {
            if (group.size() == 1) {
                assignName(group.get(0), group.get(0).getNameAsString());
                takenIn(takenByPackage, group.get(0)).add(group.get(0).getNameAsString().toLowerCase(Locale.ROOT));
            }
        }
        Map<String, String> renamed = new TreeMap<>();
        for (List<TypeDeclaration<?>> group : byKey.values()) {
            if (group.size() == 1) {
                continue;
            }
            group.sort(Comparator.comparing(ProtoGenerator::fullyQualifiedName));
            Set<String> taken = takenIn(takenByPackage, group.get(0));
            List<String> names = disambiguate(group, taken);
            for (int i = 0; i < group.size(); i++) {
                assignName(group.get(i), names.get(i));
                taken.add(names.get(i).toLowerCase(Locale.ROOT));
                renamed.put(fullyQualifiedName(group.get(i)), protoNames.get(group.get(i)));
            }
        }
        return renamed;
    }

    private void assignName(TypeDeclaration<?> type, String baseName) {
        baseNames.put(type, baseName);
        protoNames.put(type, baseName + nameSuffix);
    }

    private Set<String> takenIn(Map<String, Set<String>> takenByPackage, TypeDeclaration<?> type) {
        return takenByPackage.computeIfAbsent(usePojoPackages ? protoPackage(type) : "", k -> new HashSet<>());
    }

    private static List<String> disambiguate(List<TypeDeclaration<?>> group, Set<String> taken) {
        int maxDepth = group.stream().mapToInt(t -> packageSegments(t).size()).max().orElse(0);
        for (int depth = 1; depth <= maxDepth; depth++) {
            List<String> names = new ArrayList<>();
            for (TypeDeclaration<?> type : group) {
                names.add(packagePrefix(type, depth) + type.getNameAsString());
            }
            Set<String> lower = new HashSet<>();
            boolean unique = names.stream().allMatch(n -> lower.add(n.toLowerCase(Locale.ROOT)) && !taken.contains(n.toLowerCase(Locale.ROOT)));
            if (unique) {
                return names;
            }
        }
        // Same package (only possible on case-sensitive file systems): fall back to a numeric suffix
        List<String> names = new ArrayList<>();
        for (int i = 0; i < group.size(); i++) {
            String base = packagePrefix(group.get(i), 1) + group.get(i).getNameAsString();
            String candidate = base + (i + 1);
            for (int n = i + 1; taken.contains(candidate.toLowerCase(Locale.ROOT)); n += group.size()) {
                candidate = base + n;
            }
            names.add(candidate);
        }
        return names;
    }

    private static List<String> packageSegments(TypeDeclaration<?> type) {
        return type.findCompilationUnit()
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(pd -> List.of(pd.getNameAsString().split("\\.")))
                .orElse(List.of());
    }

    /** Last {@code depth} package segments in PascalCase: {@code com.x.fare_calc, 1 -> FareCalc}. */
    static String packagePrefix(TypeDeclaration<?> type, int depth) {
        List<String> segments = packageSegments(type);
        StringBuilder prefix = new StringBuilder();
        for (String segment : segments.subList(Math.max(0, segments.size() - depth), segments.size())) {
            for (String word : segment.split("_")) {
                if (!word.isEmpty()) {
                    prefix.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                }
            }
        }
        return prefix.toString();
    }

    private static String fullyQualifiedName(TypeDeclaration<?> type) {
        return type.getFullyQualifiedName().orElse(type.getNameAsString());
    }

    /**
     * The proto name of a top-level type: its Java name (unless {@link #registerTypes} renamed it)
     * followed by the configured name suffix.
     */
    public String protoName(TypeDeclaration<?> type) {
        return protoNames.getOrDefault(type, type.getNameAsString() + nameSuffix);
    }

    /** {@link #protoName} without the name suffix. */
    String baseName(TypeDeclaration<?> type) {
        return baseNames.getOrDefault(type, type.getNameAsString());
    }

    private String javaPackage(Node node) {
        return node.findCompilationUnit()
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(pd -> pd.getNameAsString())
                .orElse("");
    }

    /**
     * The proto package a type (or the types of a compilation unit) is generated in: with
     * {@link Options#usePojoPackages}, its Java package followed by {@link Options#javaPackageSuffix}
     * ({@code com.x.dtos -> com.x.dtos.proto}; none for classes in the default package), so that it equals
     * the generated {@code java_package}. Otherwise the {@link Options#sharedPackage}.
     */
    public String protoPackage(Node node) {
        if (!usePojoPackages) {
            return sharedPackage;
        }
        String pkg = node == null ? "" : javaPackage(node);
        return pkg.isEmpty() ? "" : pkg + javaPackageSuffix;
    }

    /** {@code option java_package} for a file in the given proto package. */
    String javaPackageFor(String protoPackage) {
        // With usePojoPackages the suffix is already part of the proto package
        return usePojoPackages ? protoPackage : protoPackage + javaPackageSuffix;
    }

    /** Directory, relative to the destination, holding the files of a proto package ("" or ending in '/'). */
    String directoryOf(String protoPackage) {
        return usePojoPackages && !protoPackage.isEmpty() ? protoPackage.replace('.', '/') + "/" : "";
    }

    /**
     * Path of the .proto file declaring a top-level type, relative to the destination directory. It is also
     * the path used to import it: {@code Address.proto}, or {@code com/x/dtos/proto/Address.proto} with usePojoPackages
     * (and javaPackageSuffix {@code ".proto"}).
     */
    public String protoFile(TypeDeclaration<?> type) {
        return directoryOf(protoPackage(type)) + protoName(type) + ".proto";
    }

    /**
     * How {@code from} refers to a top-level type: its proto name, qualified with its proto package when that
     * differs from the package of {@code from}.
     */
    String qualify(String name, TypeDeclaration<?> target, String fromPackage) {
        String targetPackage = protoPackage(target);
        if (!usePojoPackages || targetPackage.isEmpty() || targetPackage.equals(fromPackage)) {
            return name;
        }
        return targetPackage + "." + name;
    }

    /** All proto names of the registered top-level types. */
    public Set<String> registeredProtoNames() {
        return new HashSet<>(protoNames.values());
    }

    private Optional<String> primaryProtoName(CompilationUnit cu) {
        return cu.getPrimaryType().map(this::protoName);
    }

    public String generateHeader(String packageName, Set<String> imports) {
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("syntax = \"proto3\";\n\n");
        if (packageName != null && !packageName.isEmpty()) {
            headerBuilder.append("package ").append(packageName).append(";\n\n");
            headerBuilder.append("option java_package = \"").append(javaPackageFor(packageName)).append("\";\n");
            headerBuilder.append("option java_multiple_files = true;\n\n");
        }
        if (!imports.isEmpty()) {
            for (String anImport : imports) {
                headerBuilder.append("import \"").append(anImport).append("\";\n");
            }
            headerBuilder.append("\n");
        }
        return headerBuilder.toString();
    }

    public String generateMessages(List<CompilationUnit> cus, List<EnumDeclaration> enumDeclarations) {
        StringBuilder messages = new StringBuilder();
        for (CompilationUnit cu : cus) {
            if (cu.getPrimaryType().isPresent() && !cu.getPrimaryType().get().isEnumDeclaration()) {
                messages.append(generateMessage(cu, enumDeclarations));
            }
        }
        return messages.toString();
    }

    public String generateMessage(CompilationUnit cu, List<EnumDeclaration> enumDeclarations) {
        StringBuilder messageBuilder = new StringBuilder();
        primaryProtoName(cu).ifPresent(className -> {
            messageBuilder.append("message ").append(className).append(" {\n");

            AtomicInteger index = new AtomicInteger(1);
            cu.findAll(FieldDeclaration.class).stream()
                    .filter(field -> !field.isStatic()) // e.g. serialVersionUID
                    .forEach(field -> {
                for (VariableDeclarator variable : field.getVariables()) {
                    String fieldName = variable.getNameAsString();
                    String fieldType = variable.getType().asString();
                    String protoType = getProtoType(fieldType, enumDeclarations, cu, false);
                    messageBuilder.append(fieldLine(protoType, fieldName, index.getAndIncrement()));
                }
            });

            messageBuilder.append("}\n\n");
        });
        return messageBuilder.toString();
    }
    
    /** One field declaration, with the {@code optional} label added to singular fields when {@link Options#optionalFields} is on. */
    private String fieldLine(String protoType, String fieldName, int number) {
        boolean singular = !protoType.startsWith("repeated ") && !protoType.startsWith("map<");
        String label = optionalFields && singular ? "optional " : "";
        return String.format("  %s%s %s = %d;\n", label, protoType, fieldName, number);
    }

    public String generateMessageWithNestedEnums(CompilationUnit cu, List<EnumDeclaration> nestedEnums, List<EnumDeclaration> allEnums) {
        StringBuilder messageBuilder = new StringBuilder();
        primaryProtoName(cu).ifPresent(className -> {
            messageBuilder.append("message ").append(className).append(" {\n");

            for (EnumDeclaration nestedEnum : nestedEnums) {
                messageBuilder.append(generateEnum(nestedEnum));
            }

            StringBuilder fieldsBuilder = new StringBuilder();
            if (!(cu.getPrimaryType().isPresent() && cu.getPrimaryType().get().isClassOrInterfaceDeclaration() && cu.getPrimaryType().get().asClassOrInterfaceDeclaration().isInterface())) {
                AtomicInteger index = new AtomicInteger(1);
                cu.findAll(FieldDeclaration.class).stream()
                        .filter(field -> !field.isStatic()) // Filter out static fields
                        .filter(field -> field.getParentNode().isPresent() && field.getParentNode().get().equals(cu.getPrimaryType().get()))
                        .forEach(field -> {
                            for (VariableDeclarator variable : field.getVariables()) {
                                String fieldName = variable.getNameAsString();
                                String fieldType = variable.getType().asString();
                                String protoType = getProtoType(fieldType, allEnums, cu, true);
                                fieldsBuilder.append(fieldLine(protoType, fieldName, index.getAndIncrement()));
                            }
                        });
            }
            // Wrappers discovered while mapping the fields, whose element lives in this same file
            for (WrapperMessage wrapper : nestedWrappers.getOrDefault(cu, Map.of()).values()) {
                messageBuilder.append("  message ").append(wrapper.name).append(" {\n    repeated ")
                        .append(wrapper.elementType).append(" items = 1;\n  }\n");
            }
            messageBuilder.append(fieldsBuilder);
            messageBuilder.append("}\n\n");
        });
        return messageBuilder.toString();
    }

    public String generateEnums(List<EnumDeclaration> enumDeclarations) {
        StringBuilder enums = new StringBuilder();
        for (EnumDeclaration enumDeclaration : enumDeclarations) {
            enums.append(generateEnum(enumDeclaration));
        }
        return enums.toString();
    }

    public String generateEnum(EnumDeclaration enumDeclaration) {
        StringBuilder enumBuilder = new StringBuilder();
        enumBuilder.append("enum ").append(enumProtoName(enumDeclaration)).append(" {\n");
        AtomicInteger index = new AtomicInteger(0);
        enumDeclaration.getEntries().forEach(enumConstant -> {
            enumBuilder.append(String.format("  %s = %d;\n", enumValueName(enumDeclaration, enumConstant.getNameAsString()), index.getAndIncrement()));
        });
        enumBuilder.append("}\n\n");
        return enumBuilder.toString();
    }

    /**
     * Returns the proto enum value name. When {@code prefixEnumNames} is enabled, the value is prefixed
     * with the enum's name in UPPER_SNAKE_CASE (e.g. {@code OrderStatus.ACTIVE -> ORDER_STATUS_ACTIVE}),
     * following the protobuf style guide and avoiding value-name clashes between enums in the same package.
     */
    String enumValueName(EnumDeclaration enumDeclaration, String constantName) {
        if (!prefixEnumNames) {
            return constantName;
        }
        // Based on the name without the configured suffix: OrderStatus -> ORDER_STATUS_, not ORDER_STATUS_PROTO_
        String prefix = toUpperSnakeCase(enumBaseName(enumDeclaration)) + "_";
        return constantName.startsWith(prefix) ? constantName : prefix + constantName;
    }

    static String toUpperSnakeCase(String name) {
        return name
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .toUpperCase();
    }

    public Set<String> getImports(CompilationUnit cu, List<EnumDeclaration> enumDeclarations) {
        Set<String> imports = new TreeSet<>();
        cu.findAll(FieldDeclaration.class).stream()
                .filter(field -> !field.isStatic()) // Filter out static fields
                .filter(field -> field.getParentNode().isPresent() && field.getParentNode().get().equals(cu.getPrimaryType().get()))
                .forEach(field -> {
            for (VariableDeclarator variable : field.getVariables()) {
                collectImports(variable.getType().asString(), cu, enumDeclarations, imports);
            }
        });
        return imports;
    }

    /** Adds the .proto imports a Java field type needs, walking arrays, collections and maps. */
    private void collectImports(String javaType, CompilationUnit cu, List<EnumDeclaration> enumDeclarations, Set<String> imports) {
        if (isRepeatedArray(javaType)) {
            collectElementImports(arrayComponentType(javaType), cu, enumDeclarations, imports);
            return;
        }
        Optional<String> elementType = collectionElementType(javaType);
        if (elementType.isPresent()) {
            collectElementImports(elementType.get(), cu, enumDeclarations, imports);
            return;
        }
        Optional<String[]> mapTypes = mapKeyValueTypes(javaType);
        if (mapTypes.isPresent()) {
            collectImports(mapTypes.get()[0], cu, enumDeclarations, imports);
            collectElementImports(mapTypes.get()[1], cu, enumDeclarations, imports);
            return;
        }
        addLeafImport(isGenericPlaceholder(javaType, cu) ? javaType : stripTypeArguments(javaType), cu, enumDeclarations, imports);
    }

    /** A map value or collection element: a nested collection there is replaced by a wrapper message. */
    private void collectElementImports(String javaType, CompilationUnit cu, List<EnumDeclaration> enumDeclarations, Set<String> imports) {
        if (isRepeatedType(javaType)) {
            WrapperMessage wrapper = wrapperFor(javaType, enumDeclarations, cu, true);
            if (!wrapper.nested) {
                imports.add(wrapper.file);
            }
        } else {
            collectImports(javaType, cu, enumDeclarations, imports);
        }
    }

    private void addLeafImport(String importType, CompilationUnit cu, List<EnumDeclaration> enumDeclarations, Set<String> imports) {
        if (isGenericPlaceholder(importType, cu)) {
            imports.add("google/protobuf/any.proto");
            return;
        }
        Optional<EnumDeclaration> enumDeclaration = resolveEnum(importType, cu, enumDeclarations);
        if (enumDeclaration.isPresent()) {
            // Nested enums are generated inside their outermost type's .proto file
            TypeDeclaration<?> outer = outermostType(enumDeclaration.get());
            if (outer != cu.getPrimaryType().orElse(null)) {
                imports.add(protoFile(outer));
            }
        } else if (!isPrimitive(importType)) {
            switch (importType) {
                case "Instant":
                case "ZonedDateTime":
                case "LocalDateTime":
                case "Date":
                case "java.util.Date":
                    imports.add("google/protobuf/timestamp.proto");
                    break;
                case "LocalDate":
                    imports.add("google/type/date.proto");
                    break;
                case "LocalTime":
                    imports.add("google/type/timeofday.proto");
                    break;
                case "Duration":
                    imports.add("google/protobuf/duration.proto");
                    break;
                case "Period":
                    // No import needed for string
                    break;
                case "Object":
                case "java.lang.Object":
                    imports.add("google/protobuf/any.proto");
                    break;
                default:
                    Optional<TypeDeclaration<?>> sourceType = resolveType(importType, cu, topLevelTypes);
                    if (sourceType.isPresent()) {
                        if (sourceType.get() != cu.getPrimaryType().orElse(null)) {
                            imports.add(protoFile(sourceType.get()));
                        }
                        break;
                    }
                    // A type not found in the sources. Nested types (Outer.Inner) are declared
                    // in the outer type's file, so import Outer.proto, never Outer.Inner.proto.
                    String protoFile = protoFileFor(importType);
                    if (!primaryProtoName(cu).map(name -> protoFile.equals(name + ".proto")).orElse(false)) {
                        imports.add(protoFile);
                    }
                    break;
            }
        }
    }

    private String getProtoType(String javaType, List<EnumDeclaration> enumDeclarations, CompilationUnit cu, boolean qualifyNestedEnums) {
        return getProtoType(javaType, enumDeclarations, cu, qualifyNestedEnums, false);
    }

    /**
     * @param topLevelScope the type is referenced from a top-level wrapper message rather than from inside the
     *                      message of {@code cu}, so nested enums of {@code cu} must be qualified too
     */
    private String getProtoType(String javaType, List<EnumDeclaration> enumDeclarations, CompilationUnit cu,
                                boolean qualifyNestedEnums, boolean topLevelScope) {
        if (isRepeatedArray(javaType)) {
            // Covers both "Type[] name" and C-style "Type name[]"; JavaParser reports both as "Type[]"
            return "repeated " + elementProtoType(arrayComponentType(javaType), enumDeclarations, cu, qualifyNestedEnums, topLevelScope);
        }
        Optional<String> elementType = collectionElementType(javaType);
        if (elementType.isPresent()) {
            return "repeated " + elementProtoType(elementType.get(), enumDeclarations, cu, qualifyNestedEnums, topLevelScope);
        }
        Optional<String[]> mapTypes = mapKeyValueTypes(javaType);
        if (mapTypes.isPresent()) {
            String keyType = getProtoType(mapTypes.get()[0], enumDeclarations, cu, qualifyNestedEnums, topLevelScope);
            String valueType = elementProtoType(mapTypes.get()[1], enumDeclarations, cu, qualifyNestedEnums, topLevelScope);
            return String.format("map<%s, %s>", keyType, valueType);
        }
        if (isGenericPlaceholder(javaType, cu)) {
            // A class type parameter (the T in ApiResponse<T>) or a wildcard: the concrete type is only
            // known at runtime, so the field is carried as google.protobuf.Any
            return "google.protobuf.Any";
        }
        // Proto messages cannot be parameterized: a field of type ApiResponse<User> refers to message ApiResponse
        javaType = stripTypeArguments(javaType);
        Optional<EnumDeclaration> enumDeclaration = resolveEnum(javaType, cu, enumDeclarations);
        if (enumDeclaration.isPresent()) {
            return protoEnumTypeName(enumDeclaration.get(), topLevelScope ? null : cu, qualifyNestedEnums, protoPackage(cu));
        }
        switch (javaType) {
            case "String":
            case "UUID":
                return "string";
            case "int":
            case "Integer":
            case "short":
            case "Short":
            case "byte":
            case "Byte":
                return "int32";
            case "char":
            case "Character":
                return "string";
            case "byte[]":
            case "Byte[]":
                return "bytes";
            case "long":
            case "Long":
                return "int64";
            case "double":
            case "Double":
                return "double";
            case "float":
            case "Float":
                return "float";
            case "boolean":
            case "Boolean":
                return "bool";
            case "Instant":
            case "ZonedDateTime":
            case "LocalDateTime":
            case "Date":
            case "java.util.Date":
                return "google.protobuf.Timestamp";
            case "LocalDate":
                return "google.type.Date";
            case "LocalTime":
                return "google.type.TimeOfDay";
            case "Duration":
                return "google.protobuf.Duration";
            case "Period":
                return "string";
            case "Object":
            case "java.lang.Object":
                // Any value: carried as google.protobuf.Any, like a class type parameter
                return "google.protobuf.Any";
            default:
                String fromPackage = protoPackage(cu);
                return resolveType(javaType, cu, topLevelTypes)
                        .map(type -> qualify(protoName(type), type, fromPackage))
                        .orElse(stripPackage(javaType));
        }
    }

    private boolean isPrimitive(String javaType) {
        switch (javaType) {
            case "String":
            case "UUID":
            case "int":
            case "Integer":
            case "short":
            case "Short":
            case "byte":
            case "Byte":
            case "char":
            case "Character":
            case "byte[]":
            case "Byte[]":
            case "long":
            case "Long":
            case "double":
            case "Double":
            case "float":
            case "Float":
            case "boolean":
            case "Boolean":
                return true;
            default:
                return false;
        }
    }

    /**
     * Resolves the enum a Java field type refers to, as seen from the given compilation unit.
     * Handles simple names ({@code TxnType}), qualified names ({@code AppConstants.TxnType}) and fully
     * qualified names. When several enums share a name, the compilation unit's own nested enums, then its
     * imports (single-type, on-demand and static), then its package are used to pick the right one.
     */
    Optional<EnumDeclaration> resolveEnum(String javaType, CompilationUnit cu, List<EnumDeclaration> enumDeclarations) {
        return resolveType(javaType, cu, enumDeclarations);
    }

    /**
     * Resolves which of the given declarations a Java type name refers to, as seen from {@code cu}
     * (same rules as {@link #resolveEnum}). Used for enums and for the registered top-level types.
     */
    <T extends TypeDeclaration<?>> Optional<T> resolveType(String javaType, CompilationUnit cu, List<T> declarations) {
        if (declarations == null || declarations.isEmpty()) {
            return Optional.empty();
        }
        List<T> candidates = new ArrayList<>();
        for (T e : declarations) {
            String fqn = e.getFullyQualifiedName().orElse(e.getNameAsString());
            if (fqn.equals(javaType) || fqn.endsWith("." + javaType)) {
                candidates.add(e);
            }
        }
        if (candidates.size() <= 1 || cu == null) {
            return candidates.stream().findFirst();
        }

        // 1. Declared in this compilation unit
        for (T e : candidates) {
            if (e.findCompilationUnit().map(c -> c == cu).orElse(false)) {
                return Optional.of(e);
            }
        }
        // 2. Brought in by an import
        String firstSegment = javaType.contains(".") ? javaType.substring(0, javaType.indexOf('.')) : javaType;
        for (ImportDeclaration imp : cu.getImports()) {
            String name = imp.getNameAsString();
            String target;
            if (imp.isAsterisk()) {
                target = name + "." + javaType;
            } else if (name.equals(firstSegment) || name.endsWith("." + firstSegment)) {
                target = name + javaType.substring(firstSegment.length());
            } else {
                continue;
            }
            Optional<T> match = findByFqn(candidates, target);
            if (match.isPresent()) {
                return match;
            }
        }
        // 3. Same package
        String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString() + ".").orElse("");
        Optional<T> samePackage = findByFqn(candidates, pkg + javaType);
        return samePackage.isPresent() ? samePackage : candidates.stream().findFirst();
    }

    private static <T extends TypeDeclaration<?>> Optional<T> findByFqn(List<T> candidates, String fqn) {
        return candidates.stream()
                .filter(e -> e.getFullyQualifiedName().map(fqn::equals).orElse(false))
                .findFirst();
    }

    /**
     * Drops a leading Java package from a type name, relying on the convention that package segments are
     * lower case: {@code com.example.Outer.Inner -> Outer.Inner}.
     */
    static String stripPackage(String javaType) {
        String[] parts = javaType.split("\\.");
        int start = 0;
        while (start < parts.length - 1 && !parts[start].isEmpty() && Character.isLowerCase(parts[start].charAt(0))) {
            start++;
        }
        return String.join(".", Arrays.copyOfRange(parts, start, parts.length));
    }

    /**
     * The .proto file that declares a type: a nested type such as {@code Outer.Inner} lives in {@code Outer.proto}.
     */
    static String protoFileFor(String javaType) {
        String type = stripPackage(javaType);
        int dot = type.indexOf('.');
        return (dot < 0 ? type : type.substring(0, dot)) + ".proto";
    }

    /** Names of the type parameters declared by the compilation unit's primary type, e.g. {@code T} for {@code ApiResponse<T>}. */
    static Set<String> typeParameterNames(CompilationUnit cu) {
        if (cu == null) {
            return Set.of();
        }
        return cu.getPrimaryType()
                .filter(type -> type instanceof NodeWithTypeParameters)
                .map(type -> ((NodeWithTypeParameters<?>) type).getTypeParameters().stream()
                        .map(TypeParameter::getNameAsString)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    /** True for a class type parameter ({@code T}) or a wildcard ({@code ?}, {@code ? extends Foo}). */
    static boolean isGenericPlaceholder(String javaType, CompilationUnit cu) {
        return javaType.equals("?") || javaType.startsWith("? ") || typeParameterNames(cu).contains(javaType);
    }

    /** {@code ApiResponse<User> -> ApiResponse}. */
    static String stripTypeArguments(String javaType) {
        int lt = javaType.indexOf('<');
        return lt < 0 ? javaType : javaType.substring(0, lt).trim();
    }

    /**
     * The outermost type enclosing the enum (the enum itself if it is top level). That type's
     * {@code .proto} file is where the enum is generated.
     */
    static TypeDeclaration<?> outermostType(EnumDeclaration enumDeclaration) {
        TypeDeclaration<?> outer = enumDeclaration;
        Node node = enumDeclaration.getParentNode().orElse(null);
        while (node != null && !(node instanceof CompilationUnit)) {
            if (node instanceof TypeDeclaration<?> typeDeclaration) {
                outer = typeDeclaration;
            }
            node = node.getParentNode().orElse(null);
        }
        return outer;
    }

    /** Proto name of the enum itself: a top-level enum may have been renamed, nested enums keep their name. */
    String enumProtoName(EnumDeclaration enumDeclaration) {
        return outermostType(enumDeclaration) == enumDeclaration ? protoName(enumDeclaration) : enumDeclaration.getNameAsString();
    }

    /** {@link #enumProtoName} without the configured name suffix. */
    String enumBaseName(EnumDeclaration enumDeclaration) {
        return outermostType(enumDeclaration) == enumDeclaration ? baseName(enumDeclaration) : enumDeclaration.getNameAsString();
    }

    /**
     * Proto type name used to reference the enum from a message in {@code cu}. Nested enums are emitted
     * inside their outermost type's message, so they are referenced as {@code Outer.Enum}
     * (e.g. {@code AppConstants.TxnType}), except from within that same message.
     */
    String protoEnumTypeName(EnumDeclaration enumDeclaration, CompilationUnit cu, boolean qualifyNestedEnums) {
        return protoEnumTypeName(enumDeclaration, cu, qualifyNestedEnums, protoPackage(cu));
    }

    /**
     * @param cu          the compilation unit whose message contains the reference, or null for a top-level wrapper
     * @param fromPackage proto package of the file containing the reference; enums declared in another package
     *                    are qualified with theirs
     */
    String protoEnumTypeName(EnumDeclaration enumDeclaration, CompilationUnit cu, boolean qualifyNestedEnums, String fromPackage) {
        String enumName = enumProtoName(enumDeclaration);
        TypeDeclaration<?> outer = outermostType(enumDeclaration);
        if (outer == enumDeclaration) {
            return qualify(enumName, outer, fromPackage);
        }
        if (!qualifyNestedEnums || (cu != null && outer == cu.getPrimaryType().orElse(null))) {
            return enumName;
        }
        return qualify(protoName(outer) + "." + enumName, outer, fromPackage);
    }

    /**
     * A generated message wrapping a repeated field, used where protobuf does not allow {@code repeated}:
     * as a map value ({@code Map<String, List<MyPojo>>}) or as the element of another list
     * ({@code List<List<MyPojo>>}). {@code message MyPojoList { repeated MyPojo items = 1; }}
     */
    public static final class WrapperMessage {
        private final String name;
        private final String elementType;
        private final Set<String> imports;
        private final boolean nested;
        private final String protoPackage;
        private final String file;

        WrapperMessage(String name, String elementType, Set<String> imports, boolean nested, String protoPackage, String file) {
            this.name = name;
            this.elementType = elementType;
            this.imports = imports;
            this.nested = nested;
            this.protoPackage = protoPackage;
            this.file = file;
        }

        public String getName() {
            return name;
        }

        /** The proto package the wrapper is generated in (the package of the first message that needed it). */
        public String getProtoPackage() {
            return protoPackage;
        }

        /** Path of the wrapper's own .proto file, relative to the destination; also its import path. */
        public String getFile() {
            return file;
        }

        public String getElementType() {
            return elementType;
        }

        public Set<String> getImports() {
            return imports;
        }
    }

    /**
     * Top-level wrappers, each generated once in its own file; keyed by proto package and element proto type.
     * A wrapper lives in the package of the message using it, so its element type reference is valid there.
     */
    private final Map<String, WrapperMessage> topLevelWrappers = new TreeMap<>();
    /**
     * Wrappers nested inside a message: used when the element is declared in the same file as the message
     * using it (e.g. a class with a {@code Map<String, List<Self>>}), where a separate file would create a
     * circular import.
     */
    private final Map<CompilationUnit, Map<String, WrapperMessage>> nestedWrappers = new IdentityHashMap<>();

    /** The wrapper messages to generate as top-level messages (their own files in multi-file mode). */
    public List<WrapperMessage> getTopLevelWrappers() {
        return new ArrayList<>(topLevelWrappers.values());
    }

    public String generateWrapperMessage(WrapperMessage wrapper) {
        return "message " + wrapper.name + " {\n  repeated " + wrapper.elementType + " items = 1;\n}\n\n";
    }

    private static boolean isRepeatedType(String javaType) {
        return isRepeatedArray(javaType) || collectionElementType(javaType).isPresent();
    }

    /** Proto type of a map value or collection element; a nested collection there becomes a wrapper message. */
    private String elementProtoType(String javaType, List<EnumDeclaration> enumDeclarations, CompilationUnit cu,
                                    boolean qualifyNestedEnums, boolean topLevelScope) {
        if (isRepeatedType(javaType)) {
            return wrapperFor(javaType, enumDeclarations, cu, qualifyNestedEnums).name;
        }
        return getProtoType(javaType, enumDeclarations, cu, qualifyNestedEnums, topLevelScope);
    }

    /** Finds or creates the wrapper message for a Java collection or array type. */
    private WrapperMessage wrapperFor(String collectionType, List<EnumDeclaration> enumDeclarations, CompilationUnit cu,
                                      boolean multiFile) {
        String elementJava = collectionElementType(collectionType).orElseGet(() -> arrayComponentType(collectionType));
        boolean nested = multiFile && isDeclaredIn(elementJava, cu, enumDeclarations);
        String elementProto = elementProtoType(elementJava, enumDeclarations, cu, multiFile, !nested);
        String wrapperPackage = protoPackage(cu);
        Map<String, WrapperMessage> registry = nested
                ? nestedWrappers.computeIfAbsent(cu, k -> new TreeMap<>())
                : topLevelWrappers;
        String key = nested ? elementProto : wrapperPackage + "|" + elementProto;
        WrapperMessage existing = registry.get(key);
        if (existing != null) {
            return existing;
        }
        Set<String> imports = new TreeSet<>();
        if (!nested) {
            collectElementImports(elementJava, cu, enumDeclarations, imports);
        }
        // Nested wrappers live inside a message and cannot clash with generated Java classes: no suffix
        String suffix = nested ? "" : nameSuffix;
        String name = uniqueWrapperName(wrapperBaseName(elementProto, nameSuffix), suffix, wrapperPackage, registry);
        WrapperMessage wrapper = new WrapperMessage(name, elementProto, imports, nested, wrapperPackage,
                directoryOf(wrapperPackage) + name + ".proto");
        registry.put(key, wrapper);
        return wrapper;
    }

    private String uniqueWrapperName(String base, String suffix, String wrapperPackage, Map<String, WrapperMessage> registry) {
        Set<String> taken = new HashSet<>();
        protoNames.forEach((type, n) -> {
            if (!usePojoPackages || protoPackage(type).equals(wrapperPackage)) {
                taken.add(n.toLowerCase(Locale.ROOT));
            }
        });
        topLevelWrappers.values().stream()
                .filter(w -> w.protoPackage.equals(wrapperPackage))
                .forEach(w -> taken.add(w.name.toLowerCase(Locale.ROOT)));
        registry.values().forEach(w -> taken.add(w.name.toLowerCase(Locale.ROOT)));
        String name = base + suffix;
        for (int i = 2; taken.contains(name.toLowerCase(Locale.ROOT)); i++) {
            name = base + i + suffix;
        }
        return name;
    }

    /**
     * {@code MyPojo -> MyPojoList}, {@code string -> StringList},
     * {@code google.protobuf.Timestamp -> TimestampList}, {@code AppConstants.TxnType -> AppConstantsTxnTypeList}.
     * A wrapper whose base name is taken by a generated type gets a numeric suffix.
     */
    static String wrapperBaseName(String elementProto) {
        return wrapperBaseName(elementProto, "");
    }

    /**
     * Like {@link #wrapperBaseName(String)}, dropping {@code nameSuffix} from each name part so that the suffix is
     * only appended once, at the end: {@code AddressProto -> AddressList} (the caller adds the suffix:
     * {@code AddressListProto}).
     */
    static String wrapperBaseName(String elementProto, String nameSuffix) {
        String[] parts = elementProto.split("\\.");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty() || (parts.length > 1 && Character.isLowerCase(part.charAt(0)))) {
                continue; // proto package such as google.protobuf or com.example.dtos
            }
            if (!nameSuffix.isEmpty() && part.endsWith(nameSuffix) && part.length() > nameSuffix.length()) {
                part = part.substring(0, part.length() - nameSuffix.length());
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name + "List";
    }

    /** Whether the innermost element type (enum or class) is declared in {@code cu}. */
    private boolean isDeclaredIn(String javaType, CompilationUnit cu, List<EnumDeclaration> enumDeclarations) {
        String leaf = javaType;
        while (isRepeatedType(leaf)) {
            Optional<String> element = collectionElementType(leaf);
            leaf = element.isPresent() ? element.get() : arrayComponentType(leaf);
        }
        leaf = stripTypeArguments(leaf);
        Optional<EnumDeclaration> enumDeclaration = resolveEnum(leaf, cu, enumDeclarations);
        if (enumDeclaration.isPresent()) {
            return enumDeclaration.get().findCompilationUnit().map(c -> c == cu).orElse(false);
        }
        return resolveType(leaf, cu, topLevelTypes)
                .flatMap(Node::findCompilationUnit)
                .map(c -> c == cu)
                .orElse(false);
    }

    private static final Pattern MAP_TYPE = Pattern.compile(
            "(?:java\\.util\\.)?(?:Map|HashMap|LinkedHashMap|TreeMap|SortedMap|NavigableMap|ConcurrentHashMap)<(.*)>");

    /** Key and value types of a Java map, splitting on the top-level comma ({@code Map<K, Map<A, B>>}). */
    static Optional<String[]> mapKeyValueTypes(String javaType) {
        Matcher matcher = MAP_TYPE.matcher(javaType.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String arguments = matcher.group(1);
        int depth = 0;
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return Optional.of(new String[]{arguments.substring(0, i).trim(), arguments.substring(i + 1).trim()});
            }
        }
        return Optional.empty();
    }

    private static final Pattern COLLECTION_TYPE = Pattern.compile(
            "(?:java\\.util\\.)?(?:List|ArrayList|LinkedList|Collection|Set|HashSet|LinkedHashSet|TreeSet|SortedSet|NavigableSet)(?:<(.*)>)?");

    /**
     * Element type of a Java list or set ({@code Set<Address> -> Address}); these all become repeated fields.
     * A raw collection without type arguments holds Objects, which map to google.protobuf.Any.
     */
    static Optional<String> collectionElementType(String javaType) {
        Matcher matcher = COLLECTION_TYPE.matcher(javaType.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1) == null ? "Object" : matcher.group(1).trim());
    }

    /** Java arrays become repeated fields, except byte arrays which map to the proto {@code bytes} scalar. */
    static boolean isRepeatedArray(String javaType) {
        return javaType.endsWith("[]") && !javaType.equals("byte[]") && !javaType.equals("Byte[]");
    }

    static String arrayComponentType(String javaType) {
        String component = javaType.substring(0, javaType.length() - 2).trim();
        if (component.endsWith("[]") && !component.equals("byte[]") && !component.equals("Byte[]")) {
            throw new IllegalArgumentException("Multi-dimensional arrays are not supported by protobuf: " + javaType
                    + ". Wrap the inner array in a message type instead.");
        }
        return component;
    }

}
