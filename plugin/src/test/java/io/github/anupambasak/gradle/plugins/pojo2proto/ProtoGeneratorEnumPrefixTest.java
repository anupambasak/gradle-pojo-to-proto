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
import com.github.javaparser.ast.body.EnumDeclaration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtoGeneratorEnumPrefixTest {

    /**
     * Parses source as if it were read from a file. JavaParser derives a compilation unit's primary type from its
     * file name, and the generator relies on the primary type, so string-parsed units need a storage path.
     */
    static CompilationUnit parse(String source) {
        CompilationUnit cu = StaticJavaParser.parse(source);
        String typeName = cu.getTypes().get(0).getNameAsString();
        cu.setStorage(Path.of(typeName + ".java"));
        return cu;
    }

    private static EnumDeclaration parseEnum(String source) {
        return parse(source).findFirst(EnumDeclaration.class).orElseThrow();
    }

    @Test
    void enumValuesAreNotPrefixedByDefault() {
        EnumDeclaration e = parseEnum("public enum OrderStatus { ACTIVE, CANCELLED }");

        String proto = new ProtoGenerator().generateEnum(e);

        assertEquals("enum OrderStatus {\n  ACTIVE = 0;\n  CANCELLED = 1;\n}\n\n", proto);
    }

    @Test
    void enumValuesAreNotPrefixedWhenDisabled() {
        EnumDeclaration e = parseEnum("public enum OrderStatus { ACTIVE, CANCELLED }");

        String proto = new ProtoGenerator(false).generateEnum(e);

        assertEquals("enum OrderStatus {\n  ACTIVE = 0;\n  CANCELLED = 1;\n}\n\n", proto);
    }

    @Test
    void enumValuesArePrefixedWithUpperSnakeEnumName() {
        EnumDeclaration e = parseEnum("public enum OrderStatus { ACTIVE, CANCELLED }");

        String proto = new ProtoGenerator(true).generateEnum(e);

        assertEquals("enum OrderStatus {\n  ORDER_STATUS_ACTIVE = 0;\n  ORDER_STATUS_CANCELLED = 1;\n}\n\n", proto);
    }

    @Test
    void existingPrefixIsNotDuplicated() {
        EnumDeclaration e = parseEnum("public enum OrderStatus { ORDER_STATUS_UNSPECIFIED, ACTIVE }");

        String proto = new ProtoGenerator(true).generateEnum(e);

        assertTrue(proto.contains("  ORDER_STATUS_UNSPECIFIED = 0;\n"));
        assertTrue(proto.contains("  ORDER_STATUS_ACTIVE = 1;\n"));
        assertFalse(proto.contains("ORDER_STATUS_ORDER_STATUS_"));
    }

    @Test
    void nestedEnumInMessageIsPrefixed() {
        CompilationUnit cu = parse(
                "public class Booking { enum Berth { LOWER, UPPER } private Berth berth; }");
        List<EnumDeclaration> enums = cu.findAll(EnumDeclaration.class);

        String proto = new ProtoGenerator(true).generateMessageWithNestedEnums(cu, enums, enums);

        assertTrue(proto.contains("enum Berth {\n  BERTH_LOWER = 0;\n  BERTH_UPPER = 1;\n}"));
        assertTrue(proto.contains("  Berth berth = 1;"), "field type must keep the enum name, not the prefix");
    }

    @Test
    void toUpperSnakeCaseHandlesCamelCaseDigitsAndAcronyms() {
        assertEquals("ORDER_STATUS", ProtoGenerator.toUpperSnakeCase("OrderStatus"));
        assertEquals("STATUS", ProtoGenerator.toUpperSnakeCase("Status"));
        assertEquals("HTTP_METHOD", ProtoGenerator.toUpperSnakeCase("HTTPMethod"));
        assertEquals("API_V2_TYPE", ProtoGenerator.toUpperSnakeCase("ApiV2Type"));
        assertEquals("TEST_ENUM", ProtoGenerator.toUpperSnakeCase("TestEnum"));
        assertEquals("B", ProtoGenerator.toUpperSnakeCase("b"));
    }
}
