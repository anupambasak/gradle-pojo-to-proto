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
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

public class ProtoGenerator {

    public String generateHeader(String packageName, Set<String> imports) {
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("syntax = \"proto3\";\n\n");
        if (packageName != null && !packageName.isEmpty()) {
            headerBuilder.append("package ").append(packageName).append(";\n\n");
            headerBuilder.append("option java_package = \"").append(packageName).append("\";\n\n");
        }
        if (!imports.isEmpty()) {
            for (String anImport : imports) {
                headerBuilder.append("import \"").append(anImport).append("\";\n");
            }
            headerBuilder.append("\n");
        }
        return headerBuilder.toString();
    }

    public String generateMessages(List<CompilationUnit> cus) {
        StringBuilder messages = new StringBuilder();
        for (CompilationUnit cu : cus) {
            messages.append(generateMessage(cu));
        }
        return messages.toString();
    }

    public String generateMessage(CompilationUnit cu) {
        StringBuilder messageBuilder = new StringBuilder();
        cu.getPrimaryTypeName().ifPresent(className -> {
            messageBuilder.append("message ").append(className).append(" {\n");

            AtomicInteger index = new AtomicInteger(1);
            cu.findAll(FieldDeclaration.class).forEach(field -> {
                for (VariableDeclarator variable : field.getVariables()) {
                    String fieldName = variable.getNameAsString();
                    String fieldType = variable.getType().asString();
                    String protoType = getProtoType(fieldType);
                    messageBuilder.append(String.format("  %s %s = %d;\n", protoType, fieldName, index.getAndIncrement()));
                }
            });

            messageBuilder.append("}\n\n");
        });
        return messageBuilder.toString();
    }

    public Set<String> getImports(CompilationUnit cu) {
        Set<String> imports = new TreeSet<>();
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            for (VariableDeclarator variable : field.getVariables()) {
                String fieldType = variable.getType().asString();
                if (!isPrimitive(fieldType)) {
                    String importType = getImportType(fieldType);
                    if (importType.equals("Instant")) {
                        imports.add("google/protobuf/timestamp.proto");
                    } else {
                        imports.add(importType + ".proto");
                    }
                }
            }
        });
        return imports;
    }

    private String getProtoType(String javaType) {
        switch (javaType) {
            case "String":
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
                return "google.protobuf.Timestamp";
            case "List<String>":
                return "repeated string";
            case "List<Integer>":
                return "repeated int32";
            case "List<Long>":
                return "repeated int64";
            case "List<Double>":
                return "repeated double";
            case "List<Float>":
                return "repeated float";
            case "List<Boolean>":
                return "repeated bool";
            default:
                if (javaType.startsWith("List<")) {
                    String nestedType = javaType.substring(5, javaType.length() - 1);
                    return "repeated " + nestedType;
                }
                return javaType;
        }
    }

    private boolean isPrimitive(String javaType) {
        return javaType.equals("String") ||
                javaType.equals("int") || javaType.equals("Integer") ||
                javaType.equals("long") || javaType.equals("Long") ||
                javaType.equals("double") || javaType.equals("Double") ||
                javaType.equals("float") || javaType.equals("Float") ||
                javaType.equals("boolean") || javaType.equals("Boolean") ||
                javaType.equals("List<String>") ||
                javaType.equals("List<Integer>") ||
                javaType.equals("List<Long>") ||
                javaType.equals("List<Double>") ||
                javaType.equals("List<Float>") ||
                javaType.equals("List<Boolean>");
    }

    private String getImportType(String javaType) {
        if (javaType.startsWith("List<")) {
            return javaType.substring(5, javaType.length() - 1);
        }
        return javaType;
    }
}
