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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtoGenerator {

    private final boolean prefixEnumNames;

    public ProtoGenerator() {
        this(false);
    }

    public ProtoGenerator(boolean prefixEnumNames) {
        this.prefixEnumNames = prefixEnumNames;
    }

    public String generateHeader(String packageName, Set<String> imports) {
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("syntax = \"proto3\";\n\n");
        if (packageName != null && !packageName.isEmpty()) {
            headerBuilder.append("package ").append(packageName).append(";\n\n");
            headerBuilder.append("option java_package = \"").append(packageName).append("\";\n");
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
        cu.getPrimaryTypeName().ifPresent(className -> {
            messageBuilder.append("message ").append(className).append(" {\n");

            AtomicInteger index = new AtomicInteger(1);
            cu.findAll(FieldDeclaration.class).forEach(field -> {
                for (VariableDeclarator variable : field.getVariables()) {
                    String fieldName = variable.getNameAsString();
                    String fieldType = variable.getType().asString();
                    String protoType = getProtoType(fieldType, enumDeclarations, cu, false);
                    messageBuilder.append(String.format("  %s %s = %d;\n", protoType, fieldName, index.getAndIncrement()));
                }
            });

            messageBuilder.append("}\n\n");
        });
        return messageBuilder.toString();
    }
    
    public String generateMessageWithNestedEnums(CompilationUnit cu, List<EnumDeclaration> nestedEnums, List<EnumDeclaration> allEnums) {
        StringBuilder messageBuilder = new StringBuilder();
        cu.getPrimaryTypeName().ifPresent(className -> {
            messageBuilder.append("message ").append(className).append(" {\n");

            for (EnumDeclaration nestedEnum : nestedEnums) {
                messageBuilder.append(generateEnum(nestedEnum));
            }

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
                                messageBuilder.append(String.format("  %s %s = %d;\n", protoType, fieldName, index.getAndIncrement()));
                            }
                        });
            }
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
        enumBuilder.append("enum ").append(enumDeclaration.getNameAsString()).append(" {\n");
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
        String prefix = toUpperSnakeCase(enumDeclaration.getNameAsString()) + "_";
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
                String fieldType = variable.getType().asString();
                List<String> importTypes = getImportTypes(fieldType);

                for (String importType : importTypes) {
                    Optional<EnumDeclaration> enumDeclaration = resolveEnum(importType, cu, enumDeclarations);
                    if (enumDeclaration.isPresent()) {
                        // Nested enums are generated inside their outermost type's .proto file
                        String outerTypeName = outermostTypeName(enumDeclaration.get());
                        if (!cu.getPrimaryTypeName().map(outerTypeName::equals).orElse(false)) {
                            imports.add(outerTypeName + ".proto");
                        }
                    } else if (!isPrimitive(importType)) {
                        switch (importType) {
                            case "Instant":
                            case "ZonedDateTime":
                            case "LocalDateTime":
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
                            default:
                                imports.add(importType + ".proto");
                                break;
                        }
                    }
                }
            }
        });
        return imports;
    }

    /**
     * @param qualifyNestedEnums {@code true} when nested enums are generated inside their outer type's message
     *                           (multi-file mode), so references must be qualified as {@code Outer.Enum}.
     */
    private String getProtoType(String javaType, List<EnumDeclaration> enumDeclarations, CompilationUnit cu, boolean qualifyNestedEnums) {
        if (javaType.startsWith("List<")) {
            String nestedType = javaType.substring(5, javaType.length() - 1);
            return "repeated " + getProtoType(nestedType, enumDeclarations, cu, qualifyNestedEnums);
        }
        if(javaType.startsWith("ArrayList<")){
            String nestedType = javaType.substring(10, javaType.length() - 1);
            return "repeated " + getProtoType(nestedType, enumDeclarations, cu, qualifyNestedEnums);
        }
        if (javaType.matches("(Map|HashMap|LinkedHashMap|TreeMap)<.*,.*>")) {
            Pattern pattern = Pattern.compile("<(.*),(.*)>");
            Matcher matcher = pattern.matcher(javaType);
            if (matcher.find()) {
                String keyType = getProtoType(matcher.group(1).trim(), enumDeclarations, cu, qualifyNestedEnums);
                String valueType = getProtoType(matcher.group(2).trim(), enumDeclarations, cu, qualifyNestedEnums);
                return String.format("map<%s, %s>", keyType, valueType);
            }
        }
        Optional<EnumDeclaration> enumDeclaration = resolveEnum(javaType, cu, enumDeclarations);
        if (enumDeclaration.isPresent()) {
            return protoEnumTypeName(enumDeclaration.get(), cu, qualifyNestedEnums);
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
                return "google.protobuf.Timestamp";
            case "LocalDate":
                return "google.type.Date";
            case "LocalTime":
                return "google.type.TimeOfDay";
            case "Duration":
                return "google.protobuf.Duration";
            case "Period":
                return "string";
            default:
                return javaType;
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
     * Handles simple names ({@code TxnType}), qualified names ({@code PnrConstants.TxnType}) and fully
     * qualified names. When several enums share a name, the compilation unit's own nested enums, then its
     * imports (single-type, on-demand and static), then its package are used to pick the right one.
     */
    Optional<EnumDeclaration> resolveEnum(String javaType, CompilationUnit cu, List<EnumDeclaration> enumDeclarations) {
        if (enumDeclarations == null || enumDeclarations.isEmpty()) {
            return Optional.empty();
        }
        List<EnumDeclaration> candidates = new ArrayList<>();
        for (EnumDeclaration e : enumDeclarations) {
            String fqn = e.getFullyQualifiedName().orElse(e.getNameAsString());
            if (fqn.equals(javaType) || fqn.endsWith("." + javaType)) {
                candidates.add(e);
            }
        }
        if (candidates.size() <= 1 || cu == null) {
            return candidates.stream().findFirst();
        }

        // 1. Declared in this compilation unit
        for (EnumDeclaration e : candidates) {
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
            Optional<EnumDeclaration> match = findByFqn(candidates, target);
            if (match.isPresent()) {
                return match;
            }
        }
        // 3. Same package
        String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString() + ".").orElse("");
        Optional<EnumDeclaration> samePackage = findByFqn(candidates, pkg + javaType);
        return samePackage.isPresent() ? samePackage : candidates.stream().findFirst();
    }

    private static Optional<EnumDeclaration> findByFqn(List<EnumDeclaration> candidates, String fqn) {
        return candidates.stream()
                .filter(e -> e.getFullyQualifiedName().map(fqn::equals).orElse(false))
                .findFirst();
    }

    /**
     * Name of the outermost type enclosing the enum (the enum itself if it is top level). That type's
     * {@code .proto} file is where the enum is generated.
     */
    static String outermostTypeName(EnumDeclaration enumDeclaration) {
        String name = enumDeclaration.getNameAsString();
        Node node = enumDeclaration.getParentNode().orElse(null);
        while (node != null && !(node instanceof CompilationUnit)) {
            if (node instanceof TypeDeclaration<?> typeDeclaration) {
                name = typeDeclaration.getNameAsString();
            }
            node = node.getParentNode().orElse(null);
        }
        return name;
    }

    /**
     * Proto type name used to reference the enum from a message in {@code cu}. Nested enums are emitted
     * inside their outermost type's message, so they are referenced as {@code Outer.Enum}
     * (e.g. {@code PnrConstants.TxnType}), except from within that same message.
     */
    static String protoEnumTypeName(EnumDeclaration enumDeclaration, CompilationUnit cu, boolean qualifyNestedEnums) {
        String enumName = enumDeclaration.getNameAsString();
        String outer = outermostTypeName(enumDeclaration);
        if (!qualifyNestedEnums || outer.equals(enumName)
                || (cu != null && cu.getPrimaryTypeName().map(outer::equals).orElse(false))) {
            return enumName;
        }
        return outer + "." + enumName;
    }

    private List<String> getImportTypes(String javaType) {
        if (javaType.startsWith("List<")) {
            final List<String> l = getImportTypes(javaType.substring(5, javaType.length() - 1));
            return l;
        }
        if(javaType.startsWith("ArrayList<")){
            final List<String> l = getImportTypes(javaType.substring(10, javaType.length() - 1));
            return l;
        }
        if (javaType.matches("(Map|HashMap|LinkedHashMap|TreeMap)<.*,.*>")) {
            Pattern pattern = Pattern.compile("<(.*),(.*)>");
            Matcher matcher = pattern.matcher(javaType);
            if (matcher.find()) {
                List<String> types = new ArrayList<>();
                types.addAll(getImportTypes(matcher.group(1).trim()));
                types.addAll(getImportTypes(matcher.group(2).trim()));
                return types;
            }
        }
        return List.of(javaType);
    }
}
