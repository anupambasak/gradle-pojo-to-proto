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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtoGeneratorTypeMappingTest {

    private final ProtoGenerator generator = new ProtoGenerator();

    @Test
    void javaPrimitivesAndWrappersMapToProtoScalars() {
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example;
                import java.util.List;
                public class Scalars {
                    private short s;
                    private Short sBoxed;
                    private byte b;
                    private Byte bBoxed;
                    private char c;
                    private Character cBoxed;
                    private byte[] payload;
                    private List<Short> shorts;
                }
                """);

        String message = generator.generateMessageWithNestedEnums(cu, List.of(), List.of());

        assertTrue(message.contains("  int32 s = 1;"));
        assertTrue(message.contains("  int32 sBoxed = 2;"));
        assertTrue(message.contains("  int32 b = 3;"));
        assertTrue(message.contains("  int32 bBoxed = 4;"));
        assertTrue(message.contains("  string c = 5;"));
        assertTrue(message.contains("  string cBoxed = 6;"));
        assertTrue(message.contains("  bytes payload = 7;"));
        assertTrue(message.contains("  repeated int32 shorts = 8;"));
        assertTrue(generator.getImports(cu, List.of()).isEmpty(), "Java primitives must not produce imports");
    }
}
