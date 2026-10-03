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

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class PojoToProtoTask extends DefaultTask {

    static final String DEFAULT_NAME_SUFFIX = "Proto";
    static final String DEFAULT_JAVA_PACKAGE_SUFFIX = ".proto";

    @InputFiles
    public abstract ConfigurableFileCollection getSource();

    /** Directories or files to leave out of {@link #getSource()}; subdirectories of an excluded directory are excluded too. */
    @InputFiles
    @Optional
    public abstract ConfigurableFileCollection getExclude();

    @OutputDirectory
    public abstract DirectoryProperty getDestination();

    @Input
    @Optional
    public abstract Property<Boolean> getSingleFile();

    @Input
    @Optional
    public abstract Property<Boolean> getPrefixEnumNames();

    /** Package shared by all files with the flat layout ({@code usePojoPackages = false}); defaults to the project group. */
    @Input
    @Optional
    public abstract Property<String> getPackageName();

    @Input
    @Optional
    public abstract Property<Boolean> getUsePojoPackages();

    @Input
    @Optional
    public abstract Property<String> getNameSuffix();

    @Input
    @Optional
    public abstract Property<String> getJavaPackageSuffix();

    /** Mark singular fields {@code optional}; defaults to {@code true}. */
    @Input
    @Optional
    public abstract Property<Boolean> getOptionalFields();

    @Input
    public abstract Property<String> getProjectName();

    /** Default for {@link #getPackageName()}. */
    @Input
    public abstract Property<String> getProjectGroup();

    @TaskAction
    public void execute() {
        File destinationDirFile = getDestination().get().getAsFile();
        boolean singleFile = getSingleFile().getOrElse(false);
        boolean usePojoPackages = getUsePojoPackages().getOrElse(!singleFile);
        // packageName only applies to the flat layout; with usePojoPackages each file takes its POJO's package
        String sharedPackage = usePojoPackages ? "" : getPackageName().getOrElse(getProjectGroup().getOrElse("")).trim();
        String nameSuffix = getNameSuffix().getOrElse(DEFAULT_NAME_SUFFIX).trim();
        String javaPackageSuffix = getJavaPackageSuffix().getOrElse(DEFAULT_JAVA_PACKAGE_SUFFIX).trim();

        if (singleFile && usePojoPackages) {
            throw new GradleException("pojoToProto: 'singleFile' and 'usePojoPackages' cannot be combined: "
                    + "a single .proto file can only declare one package. Remove 'usePojoPackages = true'.");
        }
        if (!javaPackageSuffix.isEmpty() && !javaPackageSuffix.matches("(\\.[A-Za-z_][A-Za-z0-9_]*)+")) {
            throw new GradleException("pojoToProto: 'javaPackageSuffix' must start with '.' and contain valid "
                    + "Java identifiers, e.g. '.proto'; was '" + javaPackageSuffix + "'");
        }
        if (!nameSuffix.isEmpty() && !nameSuffix.matches("[A-Za-z0-9_]+")) {
            throw new GradleException("pojoToProto: 'nameSuffix' may only contain letters, digits and '_'; was '"
                    + nameSuffix + "'");
        }
        if (usePojoPackages && getPackageName().isPresent()) {
            getLogger().warn("pojoToProto: 'packageName' is ignored because 'usePojoPackages' is enabled: each .proto "
                    + "file uses the package of its POJO. Set 'usePojoPackages = false' (or 'singleFile = true') "
                    + "to put all messages in '" + getPackageName().get() + "'.");
        }
        if (usePojoPackages && nameSuffix.isEmpty() && javaPackageSuffix.isEmpty()) {
            getLogger().warn("pojoToProto: with 'usePojoPackages' and both 'nameSuffix' and 'javaPackageSuffix' empty, "
                    + "the generated Java classes get the same fully qualified names as your POJOs, which fails "
                    + "to compile if both end up on the same classpath.");
        }

        ProtoGenerator protoGenerator = new ProtoGenerator(new ProtoGenerator.Options()
                .prefixEnumNames(getPrefixEnumNames().getOrElse(false))
                .nameSuffix(nameSuffix)
                .usePojoPackages(usePojoPackages)
                .sharedPackage(sharedPackage)
                .javaPackageSuffix(javaPackageSuffix)
                .optionalFields(getOptionalFields().getOrElse(true)));

        List<Path> excludedPaths = getExclude().getFiles().stream()
                .map(f -> f.toPath().toAbsolutePath().normalize())
                .collect(Collectors.toList());

        List<CompilationUnit> cus = new ArrayList<>();
        for (Path javaFile : collectJavaFiles(excludedPaths)) {
            try {
                cus.add(StaticJavaParser.parse(javaFile));
            } catch (IOException e) {
                getLogger().error("Error parsing file: " + javaFile.getFileName(), e);
            }
        }

        // Assign proto names; types whose names clash (case-insensitively) are prefixed with their package
        protoGenerator.registerTypes(cus).forEach((javaName, protoName) ->
                getLogger().warn("pojoToProto: '" + javaName + "' clashes with another type of the same name "
                        + "(ignoring case); generated as '" + protoName + "'"));

        if (singleFile) {
            List<com.github.javaparser.ast.body.EnumDeclaration> allEnumDeclarations = new ArrayList<>();
            for (CompilationUnit cu : cus) {
                allEnumDeclarations.addAll(cu.findAll(com.github.javaparser.ast.body.EnumDeclaration.class));
            }

            // Generate the body first: wrapper messages (e.g. for Map<String, List<X>>) are discovered on the way
            String messages = protoGenerator.generateMessages(cus, allEnumDeclarations);
            String enums = protoGenerator.generateEnums(allEnumDeclarations);
            StringBuilder wrappers = new StringBuilder();
            Set<String> allImports = new TreeSet<>();
            for (ProtoGenerator.WrapperMessage wrapper : protoGenerator.getTopLevelWrappers()) {
                wrappers.append(protoGenerator.generateWrapperMessage(wrapper));
                allImports.addAll(wrapper.getImports());
            }
            for (CompilationUnit cu : cus) {
                allImports.addAll(protoGenerator.getImports(cu, allEnumDeclarations));
            }

            // Everything lives in this one file, so only external imports (google/...) remain
            Set<String> allTypeNames = protoGenerator.registeredProtoNames();
            allTypeNames.addAll(allEnumDeclarations.stream()
                    .map(com.github.javaparser.ast.body.EnumDeclaration::getNameAsString)
                    .collect(Collectors.toSet()));
            protoGenerator.getTopLevelWrappers().forEach(w -> allTypeNames.add(w.getName()));

            allImports.removeIf(anImport -> allTypeNames.contains(anImport.replace(".proto", "")));

            String header = protoGenerator.generateHeader(sharedPackage, allImports);
            String protoContent = header + messages + enums + wrappers;

            writeProto(destinationDirFile, getProjectName().get() + ".proto", protoContent);
        } else {
            List<com.github.javaparser.ast.body.EnumDeclaration> allEnumDeclarations = new ArrayList<>();
            for (CompilationUnit cu : cus) {
                allEnumDeclarations.addAll(cu.findAll(com.github.javaparser.ast.body.EnumDeclaration.class));
            }

            for (CompilationUnit cu : cus) {
                if (cu.getPrimaryType().isPresent() && cu.getPrimaryType().get().isClassOrInterfaceDeclaration() && !cu.getPrimaryType().get().isEnumDeclaration()) {
                    List<com.github.javaparser.ast.body.EnumDeclaration> nestedEnums = cu.getPrimaryType().get().findAll(com.github.javaparser.ast.body.EnumDeclaration.class);

                    Set<String> imports = protoGenerator.getImports(cu, allEnumDeclarations);
                    String header = protoGenerator.generateHeader(protoGenerator.protoPackage(cu), imports);
                    String message = protoGenerator.generateMessageWithNestedEnums(cu, nestedEnums, allEnumDeclarations);
                    writeProto(destinationDirFile, protoGenerator.protoFile(cu.getPrimaryType().get()), header + message);
                }
            }
            for (com.github.javaparser.ast.body.EnumDeclaration enumDeclaration : allEnumDeclarations) {
                if (enumDeclaration.getParentNode().isPresent() && enumDeclaration.getParentNode().get() instanceof CompilationUnit) {
                    String header = protoGenerator.generateHeader(protoGenerator.protoPackage(enumDeclaration), new TreeSet<>());
                    String enumContent = protoGenerator.generateEnum(enumDeclaration);
                    writeProto(destinationDirFile, protoGenerator.protoFile(enumDeclaration), header + enumContent);
                }
            }
            // Wrapper messages for collections that protobuf cannot repeat directly, e.g. the value of a
            // Map<String, List<MyPojo>> becomes MyPojoList { repeated MyPojo items = 1; } in MyPojoList.proto
            for (ProtoGenerator.WrapperMessage wrapper : protoGenerator.getTopLevelWrappers()) {
                String protoContent = protoGenerator.generateHeader(wrapper.getProtoPackage(), wrapper.getImports())
                        + protoGenerator.generateWrapperMessage(wrapper);
                writeProto(destinationDirFile, wrapper.getFile(), protoContent);
            }
        }
    }

    /** Writes a .proto file at a path relative to the destination, creating package directories as needed. */
    private void writeProto(File destinationDir, String relativePath, String content) {
        try {
            Path protoFilePath = destinationDir.toPath().resolve(relativePath);
            Files.createDirectories(protoFilePath.getParent());
            Files.write(protoFilePath, content.getBytes());
            getLogger().lifecycle("Generated " + protoFilePath);
        } catch (IOException e) {
            getLogger().error("Error writing proto file " + relativePath, e);
        }
    }

    /**
     * Collects the .java files from {@link #getSource()} (files, or directories walked recursively),
     * skipping any file that is, or is inside, one of the excluded paths.
     */
    List<Path> collectJavaFiles(List<Path> excludedPaths) {
        Set<Path> javaFiles = new LinkedHashSet<>();
        for (File sourceEntry : getSource()) {
            Path sourcePath = sourceEntry.toPath().toAbsolutePath().normalize();
            if (Files.isRegularFile(sourcePath) && sourcePath.toString().endsWith(".java")) {
                javaFiles.add(sourcePath);
            } else if (Files.isDirectory(sourcePath)) {
                try (Stream<Path> walk = Files.walk(sourcePath)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".java"))
                            .map(p -> p.toAbsolutePath().normalize())
                            .forEach(javaFiles::add);
                } catch (IOException e) {
                    getLogger().error("Error reading java files from directory: " + sourcePath, e);
                }
            }
        }
        List<Path> included = new ArrayList<>();
        for (Path javaFile : javaFiles) {
            if (isExcluded(javaFile, excludedPaths)) {
                getLogger().info("Excluded " + javaFile);
            } else {
                included.add(javaFile);
            }
        }
        return included;
    }

    static boolean isExcluded(Path javaFile, List<Path> excludedPaths) {
        for (Path excluded : excludedPaths) {
            // Path.startsWith compares whole name elements, so "dtos" does not match "dtos2"
            if (javaFile.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }
}
