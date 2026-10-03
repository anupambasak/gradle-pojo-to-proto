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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtoGeneratorOptionalFieldsTest {

    private static final String SOURCE = """
            package com.example;
            import java.time.Instant;
            import java.util.List;
            import java.util.Map;
            public class Order {
                public enum Status { NEW, DONE }
                private int qty;
                private Integer boxedQty;
                private boolean paid;
                private String note;
                private byte[] payload;
                private Status status;
                private Instant createdAt;
                private List<String> tags;
                private Map<String, Integer> counts;
                private int[] codes;
            }
            """;

    private static String generate(boolean optionalFields) {
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse(SOURCE);
        List<EnumDeclaration> enums = cu.findAll(EnumDeclaration.class);
        return new ProtoGenerator(new ProtoGenerator.Options().optionalFields(optionalFields))
                .generateMessageWithNestedEnums(cu, enums, enums);
    }

    @Test
    void singularFieldsAreOptionalButRepeatedAndMapFieldsAreNot() {
        String message = generate(true);

        assertTrue(message.contains("  optional int32 qty = 1;"));
        assertTrue(message.contains("  optional int32 boxedQty = 2;"));
        assertTrue(message.contains("  optional bool paid = 3;"));
        assertTrue(message.contains("  optional string note = 4;"));
        assertTrue(message.contains("  optional bytes payload = 5;"));
        assertTrue(message.contains("  optional Status status = 6;"), message);
        assertTrue(message.contains("  optional google.protobuf.Timestamp createdAt = 7;"));
        assertTrue(message.contains("  repeated string tags = 8;"));
        assertTrue(message.contains("  map<string, int32> counts = 9;"));
        assertTrue(message.contains("  repeated int32 codes = 10;"));
        assertFalse(message.contains("optional repeated"));
        assertFalse(message.contains("optional map<"));
        // enum values are not fields
        assertFalse(message.contains("optional NEW"));
    }

    @Test
    void offByDefaultInTheGenerator() {
        String message = generate(false);

        assertFalse(message.contains("optional"));
        assertTrue(message.contains("  int32 qty = 1;"));
    }

    @Test
    void flatGenerateMessageAlsoHonoursTheOption() {
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example;
                public class Flat { private long id; private java.util.List<Long> ids; }
                """);
        String message = new ProtoGenerator(new ProtoGenerator.Options().optionalFields(true))
                .generateMessage(cu, List.of());

        assertTrue(message.contains("  optional int64 id = 1;"));
        assertTrue(message.contains("  repeated int64 ids = 2;"));
    }
}
