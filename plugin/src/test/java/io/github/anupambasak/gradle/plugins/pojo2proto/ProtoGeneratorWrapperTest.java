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
import com.github.javaparser.ast.body.EnumDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Protobuf does not allow a repeated field as a map value or as a list element, so
 * Map<String, List<MyPojo>> becomes map<string, MyPojoList> with message MyPojoList { repeated MyPojo items = 1; }.
 */
class ProtoGeneratorWrapperTest {

    private static CompilationUnit parse(String source) {
        return ProtoGeneratorEnumPrefixTest.parse(source);
    }

    private static final String MY_POJO = """
            package com.example;
            public class MyPojo { private String name; private int qty; }
            """;

    private static List<EnumDeclaration> enums(List<CompilationUnit> cus) {
        List<EnumDeclaration> enums = new ArrayList<>();
        cus.forEach(cu -> enums.addAll(cu.findAll(EnumDeclaration.class)));
        return enums;
    }

    private static String message(ProtoGenerator generator, CompilationUnit cu, List<EnumDeclaration> allEnums) {
        List<EnumDeclaration> nested = cu.getPrimaryType().orElseThrow().findAll(EnumDeclaration.class);
        return generator.generateMessageWithNestedEnums(cu, nested, allEnums);
    }

    private static Set<String> wrapperNames(ProtoGenerator generator) {
        return generator.getTopLevelWrappers().stream().map(ProtoGenerator.WrapperMessage::getName).collect(Collectors.toSet());
    }

    @Test
    void mapOfListBecomesMapOfWrapperMessage() {
        CompilationUnit myPojo = parse(MY_POJO);
        CompilationUnit response = parse("""
                package com.example;
                import java.util.*;
                public class Response { private Map<String, List<MyPojo>> groups; }
                """);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(List.of(myPojo, response));

        assertEquals("message Response {\n  map<string, MyPojoList> groups = 1;\n}\n\n", message(generator, response, List.of()));
        assertEquals(Set.of("MyPojoList.proto"), generator.getImports(response, List.of()));

        List<ProtoGenerator.WrapperMessage> wrappers = generator.getTopLevelWrappers();
        assertEquals(1, wrappers.size());
        ProtoGenerator.WrapperMessage wrapper = wrappers.get(0);
        assertEquals("MyPojoList", wrapper.getName());
        assertEquals(Set.of("MyPojo.proto"), wrapper.getImports());
        assertEquals("message MyPojoList {\n  repeated MyPojo items = 1;\n}\n\n", generator.generateWrapperMessage(wrapper));
    }

    @Test
    void scalarAndWellKnownElementsGetTheirOwnWrappers() {
        CompilationUnit cu = parse("""
                package com.example;
                import java.util.*;
                public class Index {
                    private Map<String, Set<String>> tagsByKey;
                    private Map<Integer, List<Date>> datesById;
                    private Map<String, Object[]> valuesByKey;
                }
                """);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(List.of(cu));

        String message = message(generator, cu, List.of());
        assertTrue(message.contains("  map<string, StringList> tagsByKey = 1;"));
        assertTrue(message.contains("  map<int32, TimestampList> datesById = 2;"));
        assertTrue(message.contains("  map<string, AnyList> valuesByKey = 3;"));
        assertEquals(Set.of("StringList.proto", "TimestampList.proto", "AnyList.proto"), generator.getImports(cu, List.of()));

        for (ProtoGenerator.WrapperMessage wrapper : generator.getTopLevelWrappers()) {
            switch (wrapper.getName()) {
                case "StringList" -> assertTrue(wrapper.getImports().isEmpty());
                case "TimestampList" -> assertEquals(Set.of("google/protobuf/timestamp.proto"), wrapper.getImports());
                case "AnyList" -> assertEquals(Set.of("google/protobuf/any.proto"), wrapper.getImports());
                default -> fail("unexpected wrapper " + wrapper.getName());
            }
        }
    }

    @Test
    void listOfListsUsesWrapperAndNestsRecursively() {
        CompilationUnit myPojo = parse(MY_POJO);
        CompilationUnit cu = parse("""
                package com.example;
                import java.util.*;
                public class Grid {
                    private List<List<MyPojo>> rows;
                    private Map<String, List<List<MyPojo>>> pagesByKey;
                }
                """);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(List.of(myPojo, cu));

        String message = message(generator, cu, List.of());
        assertTrue(message.contains("  repeated MyPojoList rows = 1;"));
        assertTrue(message.contains("  map<string, MyPojoListList> pagesByKey = 2;"));
        assertEquals(Set.of("MyPojoList", "MyPojoListList"), wrapperNames(generator));
        ProtoGenerator.WrapperMessage outer = generator.getTopLevelWrappers().stream()
                .filter(w -> w.getName().equals("MyPojoListList")).findFirst().orElseThrow();
        assertEquals("MyPojoList", outer.getElementType());
        assertEquals(Set.of("MyPojoList.proto"), outer.getImports());
    }

    @Test
    void sameElementTypeSharesOneWrapper() {
        CompilationUnit myPojo = parse(MY_POJO);
        CompilationUnit a = parse("package com.example; import java.util.*; public class A { private Map<String, List<MyPojo>> x; }");
        CompilationUnit b = parse("package com.example; import java.util.*; public class B { private Map<Long, Set<MyPojo>> y; }");
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(List.of(myPojo, a, b));

        assertTrue(message(generator, a, List.of()).contains("map<string, MyPojoList> x = 1;"));
        assertTrue(message(generator, b, List.of()).contains("map<int64, MyPojoList> y = 1;"));
        assertEquals(Set.of("MyPojoList"), wrapperNames(generator));
    }

    @Test
    void selfReferenceNestsTheWrapperToAvoidACircularImport() {
        CompilationUnit node = parse("""
                package com.example;
                import java.util.*;
                public class TreeNode {
                    private String label;
                    private Map<String, List<TreeNode>> children;
                }
                """);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(List.of(node));

        String message = message(generator, node, List.of());
        assertTrue(message.contains("  message TreeNodeList {\n    repeated TreeNode items = 1;\n  }\n"));
        assertTrue(message.contains("  map<string, TreeNodeList> children = 2;"));
        assertTrue(generator.getTopLevelWrappers().isEmpty(), "no separate TreeNodeList.proto (it would import TreeNode.proto back)");
        assertTrue(generator.getImports(node, List.of()).isEmpty());
    }

    @Test
    void ownNestedEnumElementIsWrappedInsideTheMessage() {
        CompilationUnit cu = parse("""
                package com.example;
                import java.util.*;
                public class Order {
                    public enum Status { OPEN, CLOSED }
                    private Map<String, List<Status>> statusHistory;
                }
                """);
        List<EnumDeclaration> allEnums = enums(List.of(cu));
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(List.of(cu));

        String message = message(generator, cu, allEnums);
        assertTrue(message.contains("  message StatusList {\n    repeated Status items = 1;\n  }\n"));
        assertTrue(message.contains("  map<string, StatusList> statusHistory = 1;"));
        assertTrue(generator.getTopLevelWrappers().isEmpty());
    }

    @Test
    void enumNestedInAnotherTypeIsQualifiedInTheWrapper() {
        CompilationUnit constants = parse("""
                package com.example.constants;
                public interface AppConstants { enum TxnType { BOOKING, CANCELLATION } }
                """);
        CompilationUnit cu = parse("""
                package com.example;
                import java.util.*;
                import com.example.constants.AppConstants.TxnType;
                public class Ledger { private Map<String, List<TxnType>> byAccount; }
                """);
        List<CompilationUnit> cus = List.of(constants, cu);
        List<EnumDeclaration> allEnums = enums(cus);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(cus);

        assertTrue(message(generator, cu, allEnums).contains("  map<string, AppConstantsTxnTypeList> byAccount = 1;"));
        ProtoGenerator.WrapperMessage wrapper = generator.getTopLevelWrappers().get(0);
        assertEquals("AppConstants.TxnType", wrapper.getElementType());
        assertEquals(Set.of("AppConstants.proto"), wrapper.getImports());
    }

    @Test
    void wrapperNameTakenByAGeneratedClassGetsASuffix() {
        CompilationUnit myPojo = parse(MY_POJO);
        CompilationUnit existing = parse("package com.example; public class MyPojoList { private int page; }");
        CompilationUnit cu = parse("package com.example; import java.util.*; public class R { private Map<String, List<MyPojo>> g; }");
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(List.of(myPojo, existing, cu));

        assertTrue(message(generator, cu, List.of()).contains("  map<string, MyPojoList2> g = 1;"));
        assertEquals(Set.of("MyPojoList2"), wrapperNames(generator));
    }

    @Test
    void singleFileModeAlwaysUsesTopLevelWrappers() {
        CompilationUnit node = parse("""
                package com.example;
                import java.util.*;
                public class TreeNode { private Map<String, List<TreeNode>> children; }
                """);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(List.of(node));

        String message = generator.generateMessage(node, List.of());
        assertFalse(message.contains("  message TreeNodeList"));
        assertTrue(message.contains("  map<string, TreeNodeList> children = 1;"));
        assertEquals(Set.of("TreeNodeList"), wrapperNames(generator));
    }

    @Test
    void mapTypeArgumentsSplitOnTheTopLevelComma() {
        assertArrayEquals(new String[]{"String", "List<MyPojo>"}, ProtoGenerator.mapKeyValueTypes("Map<String, List<MyPojo>>").orElseThrow());
        assertArrayEquals(new String[]{"String", "Map<String, Integer>"}, ProtoGenerator.mapKeyValueTypes("HashMap<String, Map<String, Integer>>").orElseThrow());
        assertArrayEquals(new String[]{"Long", "Set<String>"}, ProtoGenerator.mapKeyValueTypes("java.util.Map<Long, Set<String>>").orElseThrow());
        assertTrue(ProtoGenerator.mapKeyValueTypes("List<String>").isEmpty());
    }

    @Test
    void wrapperBaseNames() {
        assertEquals("MyPojoList", ProtoGenerator.wrapperBaseName("MyPojo"));
        assertEquals("StringList", ProtoGenerator.wrapperBaseName("string"));
        assertEquals("Int32List", ProtoGenerator.wrapperBaseName("int32"));
        assertEquals("TimestampList", ProtoGenerator.wrapperBaseName("google.protobuf.Timestamp"));
        assertEquals("AppConstantsTxnTypeList", ProtoGenerator.wrapperBaseName("AppConstants.TxnType"));
    }
}
