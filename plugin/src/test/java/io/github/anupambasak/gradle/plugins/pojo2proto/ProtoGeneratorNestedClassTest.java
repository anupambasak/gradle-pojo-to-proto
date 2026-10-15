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

class ProtoGeneratorNestedClassTest {

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
    void nestedClassesBecomeNestedMessages() {
        CompilationUnit dto = parse("""
                package com.example;
                import java.time.LocalDateTime;
                public class TravelPolicyRequestDto {
                    private String pnrNo;
                    private LocalDateTime policyIssueDate;
                    private MessageResult messageResult;
                    private PassengerDetailsDto passengerDetailsDto;
                    public class MessageResult {
                        private String statusDescription;
                        private String statusCode;
                    }
                    public static class PassengerDetailsDto {
                        private String policyNo;
                        private Integer passengerId;
                    }
                }
                """);
        List<CompilationUnit> cus = List.of(dto);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(cus);

        String message = message(generator, dto, enums(cus));

        assertEquals("""
                message TravelPolicyRequestDto {
                  message MessageResult {
                    string statusDescription = 1;
                    string statusCode = 2;
                  }

                  message PassengerDetailsDto {
                    string policyNo = 1;
                    int32 passengerId = 2;
                  }

                  string pnrNo = 1;
                  google.protobuf.Timestamp policyIssueDate = 2;
                  MessageResult messageResult = 3;
                  PassengerDetailsDto passengerDetailsDto = 4;
                }

                """, message);
        assertEquals(Set.of("google/protobuf/timestamp.proto"), generator.getImports(dto, enums(cus)));
    }

    @Test
    void deeplyNestedClassesAndImportsOfTheirFields() {
        CompilationUnit address = parse("package com.example; public class Address { private String city; }");
        CompilationUnit outer = parse("""
                package com.example;
                import java.util.List;
                import java.time.Instant;
                public class Outer {
                    private Middle.Inner inner;
                    private List<Middle> middles;
                    public static class Middle {
                        private Address address;
                        private Inner leaf;
                        public static class Inner {
                            private Instant at;
                            private Status status;
                        }
                    }
                    public enum Status { OPEN, CLOSED }
                }
                """);
        List<CompilationUnit> cus = List.of(address, outer);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(cus);

        String message = message(generator, outer, enums(cus));

        assertTrue(message.contains("  message Middle {\n    message Inner {\n      google.protobuf.Timestamp at = 1;\n      Status status = 2;\n    }\n"), message);
        assertTrue(message.contains("    Address address = 1;\n    Middle.Inner leaf = 2;\n"), message);
        assertTrue(message.contains("  Middle.Inner inner = 1;\n  repeated Middle middles = 2;\n"), message);
        assertEquals(Set.of("Address.proto", "google/protobuf/timestamp.proto"), generator.getImports(outer, enums(cus)));
    }

    @Test
    void nestedClassIsReferencedFromAnotherFileThroughItsOuterClass() {
        CompilationUnit outer = parse("""
                package com.example.a;
                public class Outer { public static class Inner { private String x; } }
                """);
        CompilationUnit user = parse("""
                package com.example.b;
                import com.example.a.Outer;
                public class User { private Outer.Inner inner; }
                """);
        List<CompilationUnit> cus = List.of(outer, user);
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options().usePojoPackages(true).nameSuffix("Proto"));
        generator.registerTypes(cus);

        assertTrue(message(generator, user, enums(cus)).contains("  com.example.a.OuterProto.Inner inner = 1;"));
        assertEquals(Set.of("com/example/a/OuterProto.proto"), generator.getImports(user, enums(cus)));
    }

    @Test
    void nestedClassNameDoesNotLeakIntoUnrelatedFiles() {
        CompilationUnit address = parse("package com.example.a; public class Address { private String city; }");
        CompilationUnit holder = parse("""
                package com.example.b;
                public class Holder { public static class Address { private String street; } }
                """);
        CompilationUnit user = parse("""
                package com.example.a;
                public class User { private Address address; }
                """);
        List<CompilationUnit> cus = List.of(address, holder, user);
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options().usePojoPackages(true));
        generator.registerTypes(cus);

        assertTrue(message(generator, user, enums(cus)).contains("  Address address = 1;"));
        assertEquals(Set.of("com/example/a/Address.proto"), generator.getImports(user, enums(cus)));
    }

    @Test
    void singleFileModeAlsoNestsTheClasses() {
        CompilationUnit dto = parse("""
                package com.example;
                public class Dto { private Part part; public class Part { private String name; } }
                """);
        List<CompilationUnit> cus = List.of(dto);
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(cus);

        assertEquals("message Dto {\n  message Part {\n    string name = 1;\n  }\n\n  Part part = 1;\n}\n\n",
                generator.generateMessages(cus, enums(cus)));
    }
}
