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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enums nested in an interface or class are generated inside that type's message, so fields using them
 * must reference {@code Outer.Enum} and import {@code Outer.proto}, however the Java field names the type.
 */
class ProtoGeneratorNestedEnumReferenceTest {

    private static final String PNR_CONSTANTS = """
            package com.example.constants;
            public interface PnrConstants {
                enum TxnType { BOOKING, CANCELLATION }
                enum PnrStatus { FLUSHED, BOOKED }
            }
            """;

    private final ProtoGenerator generator = new ProtoGenerator();

    private static List<EnumDeclaration> allEnums(CompilationUnit... cus) {
        List<EnumDeclaration> enums = new ArrayList<>();
        for (CompilationUnit cu : cus) {
            enums.addAll(cu.findAll(EnumDeclaration.class));
        }
        return enums;
    }

    private String message(CompilationUnit cu, List<EnumDeclaration> enums) {
        List<EnumDeclaration> nested = cu.getPrimaryType().orElseThrow().findAll(EnumDeclaration.class);
        return generator.generateMessageWithNestedEnums(cu, nested, enums);
    }

    @Test
    void simpleNameFromSingleTypeImportIsQualified() {
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse(PNR_CONSTANTS);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.constants.PnrConstants.TxnType;
                public class PnrSessionDto { private TxnType txnType; }
                """);
        List<EnumDeclaration> enums = allEnums(constants, dto);

        assertTrue(message(dto, enums).contains("  PnrConstants.TxnType txnType = 1;"));
        Set<String> imports = generator.getImports(dto, enums);
        assertTrue(imports.contains("PnrConstants.proto"));
        assertFalse(imports.contains("TxnType.proto"), "no TxnType.proto is generated for a nested enum");
    }

    @Test
    void qualifiedNameIsKept() {
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse(PNR_CONSTANTS);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.constants.PnrConstants;
                public class PnrSessionDto { private PnrConstants.PnrStatus status; }
                """);
        List<EnumDeclaration> enums = allEnums(constants, dto);

        assertTrue(message(dto, enums).contains("  PnrConstants.PnrStatus status = 1;"));
        assertEquals(Set.of("PnrConstants.proto"), generator.getImports(dto, enums));
    }

    @Test
    void wildcardAndStaticImportsAreResolved() {
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse(PNR_CONSTANTS);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.constants.PnrConstants.*;
                import static com.example.constants.PnrConstants.PnrStatus;
                public class PnrSessionDto { private TxnType txnType; private PnrStatus status; }
                """);
        List<EnumDeclaration> enums = allEnums(constants, dto);

        String message = message(dto, enums);
        assertTrue(message.contains("  PnrConstants.TxnType txnType = 1;"));
        assertTrue(message.contains("  PnrConstants.PnrStatus status = 2;"));
        assertEquals(Set.of("PnrConstants.proto"), generator.getImports(dto, enums));
    }

    @Test
    void collectionsAndMapsOfNestedEnumsAreQualified() {
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse(PNR_CONSTANTS);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import java.util.*;
                import com.example.constants.PnrConstants.TxnType;
                public class PnrSessionDto {
                    private List<TxnType> history;
                    private Map<String, TxnType> byId;
                }
                """);
        List<EnumDeclaration> enums = allEnums(constants, dto);

        String message = message(dto, enums);
        assertTrue(message.contains("  repeated PnrConstants.TxnType history = 1;"));
        assertTrue(message.contains("  map<string, PnrConstants.TxnType> byId = 2;"));
    }

    @Test
    void sameSimpleNameInDifferentOuterTypesIsDisambiguatedByImport() {
        CompilationUnit pnr = ProtoGeneratorEnumPrefixTest.parse(PNR_CONSTANTS);
        CompilationUnit other = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.other;
                public interface OtherConstants { enum TxnType { X, Y } }
                """);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.other.OtherConstants.TxnType;
                public class Dto { private TxnType txnType; }
                """);
        List<EnumDeclaration> enums = allEnums(pnr, other, dto);

        assertTrue(message(dto, enums).contains("  OtherConstants.TxnType txnType = 1;"));
        assertEquals(Set.of("OtherConstants.proto"), generator.getImports(dto, enums));
    }

    @Test
    void enumNestedInOwnMessageIsNotQualifiedOrImported() {
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                public class Booking { enum Berth { LOWER, UPPER } private Berth berth; }
                """);
        List<EnumDeclaration> enums = allEnums(dto);

        assertTrue(message(dto, enums).contains("  Berth berth = 1;"));
        assertTrue(generator.getImports(dto, enums).isEmpty());
    }

    @Test
    void topLevelEnumIsReferencedBySimpleName() {
        CompilationUnit status = ProtoGeneratorEnumPrefixTest.parse("package com.example; public enum Status { ON, OFF }");
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("package com.example; public class Dto { private Status status; }");
        List<EnumDeclaration> enums = allEnums(status, dto);

        assertTrue(message(dto, enums).contains("  Status status = 1;"));
        assertEquals(Set.of("Status.proto"), generator.getImports(dto, enums));
    }

    @Test
    void singleFileModeUsesFlatNamesBecauseAllEnumsAreTopLevel() {
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse(PNR_CONSTANTS);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.constants.PnrConstants;
                public class PnrSessionDto { private PnrConstants.TxnType txnType; }
                """);
        List<EnumDeclaration> enums = allEnums(constants, dto);

        assertTrue(generator.generateMessage(dto, enums).contains("  TxnType txnType = 1;"));
    }
}
