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

import static io.github.anupambasak.gradle.plugins.pojo2proto.ProtoGeneratorEnumPrefixTest.parse;
import static org.junit.jupiter.api.Assertions.*;

class ProtoGeneratorInheritanceTest {

    private static List<EnumDeclaration> enums(List<CompilationUnit> cus) {
        List<EnumDeclaration> all = new ArrayList<>();
        cus.forEach(cu -> all.addAll(cu.findAll(EnumDeclaration.class)));
        return all;
    }

    private static String message(ProtoGenerator generator, CompilationUnit cu, List<EnumDeclaration> allEnums) {
        List<EnumDeclaration> nested = cu.getPrimaryType().orElseThrow().findAll(EnumDeclaration.class);
        return generator.generateMessageWithNestedEnums(cu, nested, allEnums);
    }

    @Test
    void inheritedFieldsComeFirstTopmostSuperclassFirst() {
        CompilationUnit base = parse("package com.example; public abstract class Base { private static final long serialVersionUID = 1L; private String id; }");
        CompilationUnit person = parse("package com.example; public class Person extends Base { private String name; }");
        CompilationUnit employee = parse("package com.example; public class Employee extends Person { private double salary; }");
        List<CompilationUnit> cus = List.of(base, person, employee);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(cus);

        String message = message(generator, employee, enums(cus));

        assertEquals("message Employee {\n  string id = 1;\n  string name = 2;\n  double salary = 3;\n}\n\n", message);
        assertTrue(message(generator, base, enums(cus)).contains("  string id = 1;"), "the superclass keeps its own message");
        assertTrue(generator.unresolvedSuperclasses().isEmpty());
    }

    @Test
    void inheritedFieldTypesAreResolvedFromTheSuperclassFile() {
        CompilationUnit address = parse("package com.example.common; public class Address { private String city; }");
        CompilationUnit base = parse("""
                package com.example.common;
                import java.time.Instant;
                import java.util.List;
                public class Audited {
                    public enum Source { WEB, API }
                    private Instant createdAt;
                    private Source source;
                    private List<Address> addresses;
                }
                """);
        CompilationUnit order = parse("""
                package com.example.orders;
                import com.example.common.Audited;
                public class Order extends Audited { private long total; }
                """);
        List<CompilationUnit> cus = List.of(address, base, order);
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options().usePojoPackages(true));
        generator.registerTypes(cus);
        List<EnumDeclaration> allEnums = enums(cus);

        String message = message(generator, order, allEnums);

        assertTrue(message.contains("  google.protobuf.Timestamp createdAt = 1;"), message);
        assertTrue(message.contains("  com.example.common.Audited.Source source = 2;"), message);
        assertTrue(message.contains("  repeated com.example.common.Address addresses = 3;"), message);
        assertTrue(message.contains("  int64 total = 4;"), message);
        assertEquals(Set.of("google/protobuf/timestamp.proto", "com/example/common/Audited.proto",
                "com/example/common/Address.proto"), generator.getImports(order, allEnums));
    }

    @Test
    void superclassTypeParametersAreBoundToTheTypeArguments() {
        CompilationUnit user = parse("package com.example; public class User { private String login; }");
        CompilationUnit page = parse("""
                package com.example;
                import java.util.List;
                import java.util.Map;
                public class Page<T, K> { private List<T> items; private Map<K, T> byKey; private T first; }
                """);
        CompilationUnit userPage = parse("package com.example; public class UserPage extends Page<User, String> { }");
        CompilationUnit raw = parse("package com.example; public class RawPage extends Page { }");
        List<CompilationUnit> cus = List.of(user, page, userPage, raw);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(cus);

        String message = message(generator, userPage, enums(cus));
        assertTrue(message.contains("  repeated User items = 1;"), message);
        assertTrue(message.contains("  map<string, User> byKey = 2;"), message);
        assertTrue(message.contains("  User first = 3;"), message);

        assertTrue(message(generator, raw, enums(cus)).contains("  google.protobuf.Any first = 3;"));
    }

    @Test
    void genericSubclassPassesItsTypeParameterThrough() {
        CompilationUnit base = parse("package com.example; public class Box<T> { private T value; }");
        CompilationUnit sub = parse("package com.example; public class Labeled<V> extends Box<V> { private String label; }");
        List<CompilationUnit> cus = List.of(base, sub);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(cus);

        String message = message(generator, sub, enums(cus));
        assertTrue(message.contains("  google.protobuf.Any value = 1;"), message);
        assertTrue(message.contains("  string label = 2;"), message);
    }

    @Test
    void hiddenFieldKeepsTheInheritedPosition() {
        CompilationUnit base = parse("package com.example; public class Base { private String id; private int version; }");
        CompilationUnit sub = parse("package com.example; public class Sub extends Base { private String id; private String extra; }");
        List<CompilationUnit> cus = List.of(base, sub);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(cus);

        assertEquals("message Sub {\n  string id = 1;\n  int32 version = 2;\n  string extra = 3;\n}\n\n",
                message(generator, sub, enums(cus)));
    }

    @Test
    void unknownSuperclassIsReportedAndOwnFieldsStillGenerated() {
        CompilationUnit sub = parse("""
                package com.example;
                import org.springframework.Base;
                public class Sub extends Base { private String name; }
                """);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(List.of(sub));

        assertTrue(message(generator, sub, List.of()).contains("  string name = 1;"));
        assertEquals(Set.of("com.example.Sub extends Base"), generator.unresolvedSuperclasses());
    }

    @Test
    void singleFileModeInheritsToo() {
        CompilationUnit base = parse("package com.example; public class Base { private String id; }");
        CompilationUnit sub = parse("package com.example; public class Sub extends Base { private String name; }");
        List<CompilationUnit> cus = List.of(base, sub);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(cus);

        assertEquals("message Sub {\n  string id = 1;\n  string name = 2;\n}\n\n", generator.generateMessage(sub, List.of()));
    }
}
