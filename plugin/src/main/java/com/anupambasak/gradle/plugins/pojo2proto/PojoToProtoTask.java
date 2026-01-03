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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public abstract class PojoToProtoTask extends DefaultTask {

    @InputDirectory
    public abstract DirectoryProperty getSource();

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
        File sourceDirFile = getSource().get().getAsFile();
        File destinationDirFile = getDestination().get().getAsFile();
        boolean singleFile = getSingleFile().getOrElse(false);
        String packageName = getPackageName().getOrElse(getProjectGroup().get());

        if (sourceDirFile.exists() && sourceDirFile.isDirectory()) {
            File[] javaFiles = sourceDirFile.listFiles((dir, name) -> name.endsWith(".java"));
            if (javaFiles != null) {
                Arrays.sort(javaFiles);
                List<CompilationUnit> cus = new ArrayList<>();
                for (File javaFile : javaFiles) {
                    try {
                        cus.add(StaticJavaParser.parse(javaFile));
                    } catch (IOException e) {
                        getLogger().error("Error parsing file: " + javaFile.getName(), e);
                    }
                }

                if (singleFile) {
                    Set<String> allImports = new TreeSet<>();
                    for (CompilationUnit cu : cus) {
                        allImports.addAll(protoGenerator.getImports(cu));
                    }

                    Set<String> allTypeNames = cus.stream()
                            .flatMap(cu -> cu.getPrimaryTypeName().stream())
                            .collect(Collectors.toSet());
                    allImports.removeIf(anImport -> allTypeNames.contains(anImport.replace(".proto", "")));

                    String header = protoGenerator.generateHeader(packageName, allImports);
                    String messages = protoGenerator.generateMessages(cus);
                    String protoContent = header + messages;

                    try {
                        Path protoFilePath = Paths.get(destinationDirFile.getAbsolutePath(), getProjectName().get() + ".proto");
                        Files.write(protoFilePath, protoContent.getBytes());
                        getLogger().lifecycle("Generated " + protoFilePath);
                    } catch (IOException e) {
                        getLogger().error("Error writing proto file", e);
                    }
                } else {
                    for (CompilationUnit cu : cus) {
                        Set<String> imports = protoGenerator.getImports(cu);
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
                }
            }
        }
    }
}
