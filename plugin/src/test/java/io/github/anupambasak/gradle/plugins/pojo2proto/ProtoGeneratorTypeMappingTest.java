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

    @Test
    void javaUtilDateMapsToTimestamp() {
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example;
                import java.util.Date;
                import java.util.List;
                import java.util.Map;
                public class Dates {
                    private Date created;
                    private java.util.Date updated;
                    private List<Date> history;
                    private Map<String, Date> byUser;
                }
                """);

        String message = generator.generateMessageWithNestedEnums(cu, List.of(), List.of());

        assertTrue(message.contains("  google.protobuf.Timestamp created = 1;"));
        assertTrue(message.contains("  google.protobuf.Timestamp updated = 2;"));
        assertTrue(message.contains("  repeated google.protobuf.Timestamp history = 3;"));
        assertTrue(message.contains("  map<string, google.protobuf.Timestamp> byUser = 4;"));
        assertEquals(java.util.Set.of("google/protobuf/timestamp.proto"), generator.getImports(cu, List.of()));
    }

    @Test
    void javaArraysMapToRepeatedFields() {
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example;
                public class Order {
                    private LineItem items[];
                    private LineItem[] moreItems;
                    private String[] tags;
                    private int scores[];
                    private java.util.Date[] timestamps;
                    private byte[] payload;
                    private byte[][] chunks;
                }
                """);

        String message = generator.generateMessageWithNestedEnums(cu, List.of(), List.of());

        assertFalse(message.contains("[]"), "array brackets must not leak into the proto");
        assertTrue(message.contains("  repeated LineItem items = 1;"));
        assertTrue(message.contains("  repeated LineItem moreItems = 2;"));
        assertTrue(message.contains("  repeated string tags = 3;"));
        assertTrue(message.contains("  repeated int32 scores = 4;"));
        assertTrue(message.contains("  repeated google.protobuf.Timestamp timestamps = 5;"));
        assertTrue(message.contains("  bytes payload = 6;"));
        assertTrue(message.contains("  repeated bytes chunks = 7;"));
        assertEquals(java.util.Set.of("LineItem.proto", "google/protobuf/timestamp.proto"),
                generator.getImports(cu, List.of()));
    }

    @Test
    void arraysOfNestedEnumsAreQualified() {
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.constants;
                public interface AppConstants { enum TxnType { BOOKING, CANCELLATION } }
                """);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.constants.AppConstants.TxnType;
                public class Session { private TxnType history[]; }
                """);
        List<com.github.javaparser.ast.body.EnumDeclaration> enums = new java.util.ArrayList<>();
        enums.addAll(constants.findAll(com.github.javaparser.ast.body.EnumDeclaration.class));

        assertTrue(generator.generateMessageWithNestedEnums(dto, List.of(), enums)
                .contains("  repeated AppConstants.TxnType history = 1;"));
        assertEquals(java.util.Set.of("AppConstants.proto"), generator.getImports(dto, enums));
    }

    @Test
    void multiDimensionalArraysAreRejectedWithAClearMessage() {
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example;
                public class Grid { private int[][] cells; }
                """);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> generator.generateMessageWithNestedEnums(cu, List.of(), List.of()));
        assertTrue(e.getMessage().contains("int[][]"));
    }

    @Test
    void classTypeParametersMapToAny() {
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example;
                import java.io.Serializable;
                import java.util.*;
                public class ApiResponse<T, E extends Exception> implements Serializable {
                    private static final long serialVersionUID = 1L;
                    private boolean success;
                    private T data;
                    private List<T> items;
                    private Map<String, T> byKey;
                    private T[] array;
                    private E error;
                    private List<?> anything;
                }
                """);

        String message = generator.generateMessageWithNestedEnums(cu, List.of(), List.of());

        assertFalse(message.contains("serialVersionUID"));
        assertTrue(message.contains("  bool success = 1;"));
        assertTrue(message.contains("  google.protobuf.Any data = 2;"));
        assertTrue(message.contains("  repeated google.protobuf.Any items = 3;"));
        assertTrue(message.contains("  map<string, google.protobuf.Any> byKey = 4;"));
        assertTrue(message.contains("  repeated google.protobuf.Any array = 5;"));
        assertTrue(message.contains("  google.protobuf.Any error = 6;"), "bounded type parameters are Any too");
        assertTrue(message.contains("  repeated google.protobuf.Any anything = 7;"), "wildcards are Any");
        assertEquals(java.util.Set.of("google/protobuf/any.proto"), generator.getImports(cu, List.of()));
    }

    @Test
    void singleFileModeAlsoSkipsStaticsAndMapsTypeParameters() {
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example;
                public class ApiResponse<T> {
                    private static final long serialVersionUID = 1L;
                    private T data;
                }
                """);

        String message = generator.generateMessage(cu, List.of());

        assertFalse(message.contains("serialVersionUID"));
        assertTrue(message.contains("  google.protobuf.Any data = 1;"));
    }

    @Test
    void parameterizedFieldTypesDropTypeArguments() {
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example;
                import java.util.*;
                public class Envelope {
                    private ApiResponse<Address> response;
                    private List<ApiResponse<Address>> history;
                    private Map<String, ApiResponse<Address>> byId;
                }
                """);

        String message = generator.generateMessageWithNestedEnums(cu, List.of(), List.of());

        assertFalse(message.contains("<Address>"));
        assertTrue(message.contains("  ApiResponse response = 1;"));
        assertTrue(message.contains("  repeated ApiResponse history = 2;"));
        assertTrue(message.contains("  map<string, ApiResponse> byId = 3;"));
        assertEquals(java.util.Set.of("ApiResponse.proto"), generator.getImports(cu, List.of()));
    }

    @Test
    void typeParameterNameOnlyAppliesToTheDeclaringClass() {
        // "T" here is a real class, not a type parameter
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example;
                public class Holder { private T value; }
                """);

        assertTrue(generator.generateMessageWithNestedEnums(cu, List.of(), List.of()).contains("  T value = 1;"));
        assertEquals(java.util.Set.of("T.proto"), generator.getImports(cu, List.of()));
    }

    @Test
    void javaLangObjectMapsToAny() {
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example;
                import java.util.*;
                public class Envelope {
                    private Object payload;
                    private java.lang.Object qualified;
                    private List<Object> items;
                    private Map<String, Object> attributes;
                    private Object[] values;
                }
                """);

        String message = generator.generateMessageWithNestedEnums(cu, List.of(), List.of());

        assertTrue(message.contains("  google.protobuf.Any payload = 1;"));
        assertTrue(message.contains("  google.protobuf.Any qualified = 2;"));
        assertTrue(message.contains("  repeated google.protobuf.Any items = 3;"));
        assertTrue(message.contains("  map<string, google.protobuf.Any> attributes = 4;"));
        assertTrue(message.contains("  repeated google.protobuf.Any values = 5;"));
        assertEquals(java.util.Set.of("google/protobuf/any.proto"), generator.getImports(cu, List.of()));
    }

    @Test
    void setsAndOtherCollectionsMapToRepeated() {
        CompilationUnit cu = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example;
                import java.util.*;
                public class Collections {
                    private Set<String> tags;
                    private HashSet<Address> addresses;
                    private LinkedHashSet<Integer> ordered;
                    private TreeSet<Long> sorted;
                    private SortedSet<Double> sortedIface;
                    private NavigableSet<Boolean> navigable;
                    private java.util.Set<java.util.Date> dates;
                    private Collection<Address> history;
                    private LinkedList<String> queue;
                    private Set<? extends Number> numbers;
                    private Set raw;
                }
                """);

        String message = generator.generateMessageWithNestedEnums(cu, List.of(), List.of());

        assertFalse(message.contains("Set"), "no Set type may leak into the proto: " + message);
        assertTrue(message.contains("  repeated string tags = 1;"));
        assertTrue(message.contains("  repeated Address addresses = 2;"));
        assertTrue(message.contains("  repeated int32 ordered = 3;"));
        assertTrue(message.contains("  repeated int64 sorted = 4;"));
        assertTrue(message.contains("  repeated double sortedIface = 5;"));
        assertTrue(message.contains("  repeated bool navigable = 6;"));
        assertTrue(message.contains("  repeated google.protobuf.Timestamp dates = 7;"));
        assertTrue(message.contains("  repeated Address history = 8;"));
        assertTrue(message.contains("  repeated string queue = 9;"));
        assertTrue(message.contains("  repeated google.protobuf.Any numbers = 10;"), "wildcard elements are Any");
        assertTrue(message.contains("  repeated google.protobuf.Any raw = 11;"), "raw collections hold Objects");
        assertEquals(java.util.Set.of("Address.proto", "google/protobuf/timestamp.proto", "google/protobuf/any.proto"),
                generator.getImports(cu, List.of()));
    }
}
