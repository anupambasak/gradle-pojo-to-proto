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

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PojoToProtoExcludeTest {

    @TempDir
    Path projectDir;

    private Path write(String relative) throws IOException {
        Path file = projectDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "public class " + file.getFileName().toString().replace(".java", "") + " {}");
        return file;
    }

    private List<String> collect(Project project) {
        PojoToProtoTask task = (PojoToProtoTask) project.getTasks().getByName("pojoToProto");
        List<Path> excluded = task.getExclude().getFiles().stream()
                .map(f -> f.toPath().toAbsolutePath().normalize())
                .collect(Collectors.toList());
        return task.collectJavaFiles(excluded).stream()
                .map(p -> projectDir.toAbsolutePath().normalize().relativize(p).toString().replace('\\', '/'))
                .sorted()
                .collect(Collectors.toList());
    }

    @Test
    void multipleExcludedDirectoriesAndFilesAreSkipped() throws IOException {
        write("src/dtos/Person.java");
        write("src/dtos/Legacy.java");
        write("src/dtos/internal/Audit.java");
        write("src/dtos/internal/deep/AuditDetail.java");
        write("src/dtos/generated/Gen.java");
        write("src/enums/Status.java");

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(GradlePojoToProtoPlugin.class);
        PojoToProtoExtension ext = project.getExtensions().getByType(PojoToProtoExtension.class);
        ext.getSource().from(project.file("src/dtos"), project.file("src/enums"));
        ext.getExclude().from(project.file("src/dtos/internal"), project.file("src/dtos/generated"));
        ext.getExclude().from(project.file("src/dtos/Legacy.java"));

        assertEquals(List.of("src/dtos/Person.java", "src/enums/Status.java"), collect(project));
    }

    @Test
    void nothingIsExcludedByDefault() throws IOException {
        write("src/dtos/Person.java");
        write("src/dtos/internal/Audit.java");

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.getPluginManager().apply(GradlePojoToProtoPlugin.class);
        project.getExtensions().getByType(PojoToProtoExtension.class).getSource().from(project.file("src/dtos"));

        assertEquals(List.of("src/dtos/Person.java", "src/dtos/internal/Audit.java"), collect(project));
    }

    @Test
    void excludeMatchesWholePathSegmentsOnly() {
        Path excluded = Path.of("/p/src/dtos").toAbsolutePath().normalize();

        assertTrue(PojoToProtoTask.isExcluded(Path.of("/p/src/dtos/A.java").toAbsolutePath(), List.of(excluded)));
        assertTrue(PojoToProtoTask.isExcluded(Path.of("/p/src/dtos/sub/B.java").toAbsolutePath(), List.of(excluded)));
        assertFalse(PojoToProtoTask.isExcluded(Path.of("/p/src/dtos2/C.java").toAbsolutePath(), List.of(excluded)),
                "a sibling directory sharing the prefix must not be excluded");
    }
}
