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

package com.anupambasak.gradle.plugins.pojo2proto;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProtoGenerator {

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
                    String protoType = getProtoType(fieldType, enumDeclarations);
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
                                String protoType = getProtoType(fieldType, allEnums);
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
            enumBuilder.append(String.format("  %s = %d;\n", enumConstant.getNameAsString(), index.getAndIncrement()));
        });
        enumBuilder.append("}\n\n");
        return enumBuilder.toString();
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
                    if (isEnum(importType, enumDeclarations)) {
                        if (importType.contains(".")) {
                            String parentName = importType.substring(0, importType.lastIndexOf('.'));
                            if (!cu.getPrimaryTypeName().map(name -> name.equals(parentName)).orElse(false)) {
                                imports.add(parentName + ".proto");
                            }
                        } else {
                            imports.add(importType + ".proto");
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

    private String getProtoType(String javaType, List<EnumDeclaration> enumDeclarations) {
        if (javaType.startsWith("List<")) {
            String nestedType = javaType.substring(5, javaType.length() - 1);
            return "repeated " + getProtoType(nestedType, enumDeclarations);
        }
        if (javaType.matches("(Map|HashMap|LinkedHashMap|TreeMap)<.*,.*>")) {
            Pattern pattern = Pattern.compile("<(.*),(.*)>");
            Matcher matcher = pattern.matcher(javaType);
            if (matcher.find()) {
                String keyType = getProtoType(matcher.group(1).trim(), enumDeclarations);
                String valueType = getProtoType(matcher.group(2).trim(), enumDeclarations);
                return String.format("map<%s, %s>", keyType, valueType);
            }
        }
        if (isEnum(javaType, enumDeclarations)) {
            return javaType;
        }
        switch (javaType) {
            case "String":
            case "UUID":
                return "string";
            case "int":
            case "Integer":
                return "int32";
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

    private boolean isEnum(String javaType, List<EnumDeclaration> enumDeclarations) {
        if (enumDeclarations == null) {
            return false;
        }
        return enumDeclarations.stream()
                .anyMatch(e -> e.getFullyQualifiedName().map(name -> name.endsWith("." + javaType) || name.equals(javaType)).orElse(false) ||
                               e.getNameAsString().equals(javaType)
                );
    }

    private List<String> getImportTypes(String javaType) {
        if (javaType.startsWith("List<")) {
            return getImportTypes(javaType.substring(5, javaType.length() - 1));
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
