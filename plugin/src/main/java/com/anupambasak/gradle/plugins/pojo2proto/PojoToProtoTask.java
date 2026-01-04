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

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public abstract class PojoToProtoTask extends DefaultTask {

    @InputFiles
    public abstract ConfigurableFileCollection getSource();

    @OutputDirectory
    public abstract DirectoryProperty getDestination();

    @Input
    @Optional
    public abstract Property<Boolean> getSingleFile();

    @Input
    @Optional
    public abstract Property<String> getPackageName();

    @Input
    public abstract Property<String> getProjectName();

    @Input
    public abstract Property<String> getProjectGroup();

    @TaskAction
    public void execute() {
        ProtoGenerator protoGenerator = new ProtoGenerator();
        File destinationDirFile = getDestination().get().getAsFile();
        boolean singleFile = getSingleFile().getOrElse(false);
        String packageName = getPackageName().getOrElse(getProjectGroup().get());

        List<CompilationUnit> classCus = new ArrayList<>();
        List<CompilationUnit> enumCus = new ArrayList<>();
        for (File javaFile : getSource()) {
            if (javaFile.isFile() && javaFile.getName().endsWith(".java")) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(javaFile);
                    if (cu.getPrimaryType().isPresent() && cu.getPrimaryType().get().isEnumDeclaration()) {
                        enumCus.add(cu);
                    } else {
                        classCus.add(cu);
                    }
                } catch (IOException e) {
                    getLogger().error("Error parsing file: " + javaFile.getName(), e);
                }
            } else if (javaFile.isDirectory()) {
                try {
                    Files.walk(javaFile.toPath())
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .forEach(p -> {
                                try {
                                    CompilationUnit cu = StaticJavaParser.parse(p);
                                    if (cu.getPrimaryType().isPresent() && cu.getPrimaryType().get().isEnumDeclaration()) {
                                        enumCus.add(cu);
                                    } else {
                                        classCus.add(cu);
                                    }
                                } catch (IOException e) {
                                    getLogger().error("Error parsing file: " + p.getFileName().toString(), e);
                                }
                            });
                } catch (IOException e) {
                    getLogger().error("Error reading java files from directory: " + javaFile.getAbsolutePath(), e);
                }
            }
        }

        if (singleFile) {
            Set<String> allImports = new TreeSet<>();
            for (CompilationUnit cu : classCus) {
                allImports.addAll(protoGenerator.getImports(cu, enumCus));
            }

            Set<String> allTypeNames = classCus.stream()
                    .flatMap(cu -> cu.getPrimaryTypeName().stream())
                    .collect(Collectors.toSet());
            allTypeNames.addAll(enumCus.stream()
                    .flatMap(cu -> cu.getPrimaryTypeName().stream())
                    .collect(Collectors.toSet()));

            allImports.removeIf(anImport -> allTypeNames.contains(anImport.replace(".proto", "")));

            String header = protoGenerator.generateHeader(packageName, allImports);
            String messages = protoGenerator.generateMessages(classCus);
            String enums = protoGenerator.generateEnums(enumCus);
            String protoContent = header + messages + enums;

            try {
                Path protoFilePath = Paths.get(destinationDirFile.getAbsolutePath(), getProjectName().get() + ".proto");
                Files.write(protoFilePath, protoContent.getBytes());
                getLogger().lifecycle("Generated " + protoFilePath);
            } catch (IOException e) {
                getLogger().error("Error writing proto file", e);
            }
        } else {
            for (CompilationUnit cu : classCus) {
                Set<String> imports = protoGenerator.getImports(cu, enumCus);
                String header = protoGenerator.generateHeader(packageName, imports);
                String message = protoGenerator.generateMessage(cu);
                String protoContent = header + message;

                cu.getPrimaryTypeName().ifPresent(className -> {
                    try {
                        Path protoFilePath = Paths.get(destinationDirFile.getAbsolutePath(), className + ".proto");
                        Files.write(protoFilePath, protoContent.getBytes());
                        getLogger().lifecycle("Generated " + protoFilePath);
                    } catch (IOException e) {
                        getLogger().error("Error writing proto file", e);
                    }
                });
            }
            for (CompilationUnit cu : enumCus) {
                String header = protoGenerator.generateHeader(packageName, new TreeSet<>());
                String enumContent = protoGenerator.generateEnum(cu);
                String protoContent = header + enumContent;

                cu.getPrimaryTypeName().ifPresent(className -> {
                    try {
                        Path protoFilePath = Paths.get(destinationDirFile.getAbsolutePath(), className + ".proto");
                        Files.write(protoFilePath, protoContent.getBytes());
                        getLogger().lifecycle("Generated " + protoFilePath);
                    } catch (IOException e) {
                        getLogger().error("Error writing proto file", e);
                    }
                });
            }
        }
    }
}
