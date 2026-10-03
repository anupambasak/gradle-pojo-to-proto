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

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the task with {@code usePojoPackages} and {@code nameSuffix} and checks where the files land. */
class PojoToProtoTaskLayoutTest {

    @TempDir
    Path projectDir;

    private void write(String relative, String source) throws IOException {
        Path file = projectDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    private PojoToProtoExtension configure(Project project) throws IOException {
        write("src/com/example/dtos/Address.java", "package com.example.dtos;\npublic class Address { private String city; }");
        write("src/com/example/dtos/Order.java", """
                package com.example.dtos;
                import com.example.enums.OrderStatus;
                public class Order { private Address shipTo; private OrderStatus status; }
                """);
        write("src/com/example/enums/OrderStatus.java", "package com.example.enums;\npublic enum OrderStatus { ACTIVE }");
        project.getPluginManager().apply(GradlePojoToProtoPlugin.class);
        PojoToProtoExtension ext = project.getExtensions().getByType(PojoToProtoExtension.class);
        ext.getSource().from(project.file("src"));
        ext.getDestination().set(project.file("out"));
        return ext;
    }

    private static PojoToProtoTask task(Project project) {
        return (PojoToProtoTask) project.getTasks().getByName("pojoToProto");
    }

    private List<String> generatedFiles() throws IOException {
        Path out = projectDir.resolve("out");
        try (Stream<Path> walk = Files.walk(out)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> out.relativize(p).toString().replace('\\', '/'))
                    .sorted().collect(Collectors.toList());
        }
    }

    @Test
    void byDefaultFilesFollowJavaPackageWithProtoSuffixes() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        configure(project);

        task(project).execute();

        assertEquals(List.of(
                "com/example/dtos/proto/AddressProto.proto",
                "com/example/dtos/proto/OrderProto.proto",
                "com/example/enums/proto/OrderStatusProto.proto"), generatedFiles());

        // The proto package and directory include javaPackageSuffix, so they match java_package
        String order = Files.readString(projectDir.resolve("out/com/example/dtos/proto/OrderProto.proto"));
        assertTrue(order.contains("package com.example.dtos.proto;\n"), order);
        assertTrue(order.contains("option java_package = \"com.example.dtos.proto\";\n"), order);
        assertTrue(order.contains("import \"com/example/dtos/proto/AddressProto.proto\";\n"), order);
        assertTrue(order.contains("import \"com/example/enums/proto/OrderStatusProto.proto\";\n"), order);
        assertTrue(order.contains("message OrderProto {\n  optional AddressProto shipTo = 1;\n  optional com.example.enums.proto.OrderStatusProto status = 2;\n}"), order);
    }

    @Test
    void withoutJavaPackageSuffixFilesFollowThePojoPackageExactly() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        PojoToProtoExtension ext = configure(project);
        ext.getJavaPackageSuffix().set("");

        task(project).execute();

        assertEquals(List.of(
                "com/example/dtos/AddressProto.proto",
                "com/example/dtos/OrderProto.proto",
                "com/example/enums/OrderStatusProto.proto"), generatedFiles());
        String order = Files.readString(projectDir.resolve("out/com/example/dtos/OrderProto.proto"));
        assertTrue(order.contains("package com.example.dtos;\n"), order);
        assertTrue(order.contains("option java_package = \"com.example.dtos\";\n"), order);
        assertTrue(order.contains("  optional com.example.enums.OrderStatusProto status = 2;"), order);
    }

    @Test
    void defaultsCanBeSwitchedOffForTheFlatLayout() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.setGroup("com.example.proto");
        PojoToProtoExtension ext = configure(project);
        ext.getUsePojoPackages().set(false);
        ext.getNameSuffix().set("");
        ext.getJavaPackageSuffix().set("");
        ext.getOptionalFields().set(false);

        task(project).execute();

        assertEquals(List.of("Address.proto", "Order.proto", "OrderStatus.proto"), generatedFiles());
        String order = Files.readString(projectDir.resolve("out/Order.proto"));
        assertTrue(order.contains("package com.example.proto;\n"), order);
        assertTrue(order.contains("option java_package = \"com.example.proto\";\n"), order);
        assertTrue(order.contains("import \"Address.proto\";\n"), order);
        assertTrue(order.contains("message Order {\n  Address shipTo = 1;\n  OrderStatus status = 2;\n}"), order);
    }

    @Test
    void packageNameIsUsedForTheFlatLayout() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.setGroup("com.example.group");
        PojoToProtoExtension ext = configure(project);
        ext.getUsePojoPackages().set(false);
        ext.getPackageName().set("com.example.api");

        task(project).execute();

        assertEquals(List.of("AddressProto.proto", "OrderProto.proto", "OrderStatusProto.proto"), generatedFiles());
        String order = Files.readString(projectDir.resolve("out/OrderProto.proto"));
        assertTrue(order.contains("package com.example.api;\n"), order);
        assertTrue(order.contains("option java_package = \"com.example.api.proto\";\n"), order);
        assertTrue(order.contains("  optional OrderStatusProto status = 2;"), order);
    }

    @Test
    void packageNameIsUsedForTheSingleFile() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.setGroup("com.example.group");
        PojoToProtoExtension ext = configure(project);
        ext.getSingleFile().set(true);
        ext.getPackageName().set("com.example.api");

        task(project).execute();

        String proto = Files.readString(projectDir.resolve("out/" + project.getName() + ".proto"));
        assertTrue(proto.contains("package com.example.api;\n"), proto);
    }

    @Test
    void packageNameIsIgnoredWithPojoPackages() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        PojoToProtoExtension ext = configure(project);
        ext.getPackageName().set("com.example.api");

        task(project).execute();

        assertEquals(List.of(
                "com/example/dtos/proto/AddressProto.proto",
                "com/example/dtos/proto/OrderProto.proto",
                "com/example/enums/proto/OrderStatusProto.proto"), generatedFiles());
        String order = Files.readString(projectDir.resolve("out/com/example/dtos/proto/OrderProto.proto"));
        assertTrue(order.contains("package com.example.dtos.proto;\n"), order);
        assertFalse(order.contains("com.example.api"), order);
    }

    @Test
    void singleFileTurnsOffPojoPackagesByDefault() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.setGroup("com.example.proto");
        PojoToProtoExtension ext = configure(project);
        ext.getSingleFile().set(true);

        assertFalse(ext.getUsePojoPackages().get());
        task(project).execute();

        assertEquals(List.of(project.getName() + ".proto"), generatedFiles());
        String proto = Files.readString(projectDir.resolve("out/" + project.getName() + ".proto"));
        assertTrue(proto.contains("package com.example.proto;\n"), proto);
        assertTrue(proto.contains("option java_package = \"com.example.proto.proto\";\n"), proto);
        assertTrue(proto.contains("message OrderProto {"), proto);
        assertFalse(proto.contains("import \"AddressProto.proto\""), proto);
    }

    @Test
    void singleFileCannotBeCombinedWithPojoPackages() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        PojoToProtoExtension ext = configure(project);
        ext.getUsePojoPackages().set(true);
        ext.getSingleFile().set(true);

        GradleException e = assertThrows(GradleException.class, () -> task(project).execute());
        assertTrue(e.getMessage().contains("singleFile"), e.getMessage());
    }

    @Test
    void invalidJavaPackageSuffixIsRejected() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        PojoToProtoExtension ext = configure(project);
        ext.getJavaPackageSuffix().set("proto");

        GradleException e = assertThrows(GradleException.class, () -> task(project).execute());
        assertTrue(e.getMessage().contains("javaPackageSuffix"), e.getMessage());
    }
}
