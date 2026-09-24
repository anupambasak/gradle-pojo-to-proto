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

    private static final String APP_CONSTANTS = """
            package com.example.constants;
            public interface AppConstants {
                enum TxnType { BOOKING, CANCELLATION }
                enum RecordStatus { FLUSHED, BOOKED }
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
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse(APP_CONSTANTS);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.constants.AppConstants.TxnType;
                public class SessionDto { private TxnType txnType; }
                """);
        List<EnumDeclaration> enums = allEnums(constants, dto);

        assertTrue(message(dto, enums).contains("  AppConstants.TxnType txnType = 1;"));
        Set<String> imports = generator.getImports(dto, enums);
        assertTrue(imports.contains("AppConstants.proto"));
        assertFalse(imports.contains("TxnType.proto"), "no TxnType.proto is generated for a nested enum");
    }

    @Test
    void qualifiedNameIsKept() {
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse(APP_CONSTANTS);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.constants.AppConstants;
                public class SessionDto { private AppConstants.RecordStatus status; }
                """);
        List<EnumDeclaration> enums = allEnums(constants, dto);

        assertTrue(message(dto, enums).contains("  AppConstants.RecordStatus status = 1;"));
        assertEquals(Set.of("AppConstants.proto"), generator.getImports(dto, enums));
    }

    @Test
    void wildcardAndStaticImportsAreResolved() {
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse(APP_CONSTANTS);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.constants.AppConstants.*;
                import static com.example.constants.AppConstants.RecordStatus;
                public class SessionDto { private TxnType txnType; private RecordStatus status; }
                """);
        List<EnumDeclaration> enums = allEnums(constants, dto);

        String message = message(dto, enums);
        assertTrue(message.contains("  AppConstants.TxnType txnType = 1;"));
        assertTrue(message.contains("  AppConstants.RecordStatus status = 2;"));
        assertEquals(Set.of("AppConstants.proto"), generator.getImports(dto, enums));
    }

    @Test
    void collectionsAndMapsOfNestedEnumsAreQualified() {
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse(APP_CONSTANTS);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import java.util.*;
                import com.example.constants.AppConstants.TxnType;
                public class SessionDto {
                    private List<TxnType> history;
                    private Map<String, TxnType> byId;
                }
                """);
        List<EnumDeclaration> enums = allEnums(constants, dto);

        String message = message(dto, enums);
        assertTrue(message.contains("  repeated AppConstants.TxnType history = 1;"));
        assertTrue(message.contains("  map<string, AppConstants.TxnType> byId = 2;"));
    }

    @Test
    void sameSimpleNameInDifferentOuterTypesIsDisambiguatedByImport() {
        CompilationUnit appConstants = ProtoGeneratorEnumPrefixTest.parse(APP_CONSTANTS);
        CompilationUnit other = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.other;
                public interface OtherConstants { enum TxnType { X, Y } }
                """);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.other.OtherConstants.TxnType;
                public class Dto { private TxnType txnType; }
                """);
        List<EnumDeclaration> enums = allEnums(appConstants, other, dto);

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
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse(APP_CONSTANTS);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.constants.AppConstants;
                public class SessionDto { private AppConstants.TxnType txnType; }
                """);
        List<EnumDeclaration> enums = allEnums(constants, dto);

        assertTrue(generator.generateMessage(dto, enums).contains("  TxnType txnType = 1;"));
    }

    @Test
    void nestedTypeOutsideSourcesImportsOuterTypeFile() {
        // AccountingConstants lives in another package that is not part of the configured sources,
        // so the generator cannot tell SiteId is an enum; it must still import the outer type's file.
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.accounting.AccountingConstants;
                public class Payment { private AccountingConstants.SiteId siteId; }
                """);
        List<EnumDeclaration> enums = allEnums(dto);

        assertTrue(message(dto, enums).contains("  AccountingConstants.SiteId siteId = 1;"));
        Set<String> imports = generator.getImports(dto, enums);
        assertEquals(Set.of("AccountingConstants.proto"), imports);
        assertFalse(imports.contains("AccountingConstants.SiteId.proto"));
    }

    @Test
    void fullyQualifiedTypeOutsideSourcesDropsJavaPackage() {
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                public class Payment { private com.example.accounting.AccountingConstants.SiteId siteId; }
                """);
        List<EnumDeclaration> enums = allEnums(dto);

        assertTrue(message(dto, enums).contains("  AccountingConstants.SiteId siteId = 1;"));
        assertEquals(Set.of("AccountingConstants.proto"), generator.getImports(dto, enums));
    }

    @Test
    void nestedEnumInAnotherPackageInsideSourcesIsResolved() {
        CompilationUnit constants = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.accounting;
                public interface AccountingConstants { enum SiteId { NORTH, SOUTH } }
                """);
        CompilationUnit dto = ProtoGeneratorEnumPrefixTest.parse("""
                package com.example.dto;
                import com.example.accounting.AccountingConstants;
                public class Payment { private AccountingConstants.SiteId siteId; }
                """);
        List<EnumDeclaration> enums = allEnums(constants, dto);

        assertTrue(message(dto, enums).contains("  AccountingConstants.SiteId siteId = 1;"));
        assertEquals(Set.of("AccountingConstants.proto"), generator.getImports(dto, enums));
    }

    @Test
    void protoFileAndPackageStripping() {
        assertEquals("Outer.Inner", ProtoGenerator.stripPackage("com.example.Outer.Inner"));
        assertEquals("Outer.Inner", ProtoGenerator.stripPackage("Outer.Inner"));
        assertEquals("Address", ProtoGenerator.stripPackage("Address"));
        assertEquals("Outer.proto", ProtoGenerator.protoFileFor("com.example.Outer.Inner"));
        assertEquals("Outer.proto", ProtoGenerator.protoFileFor("Outer.Inner"));
        assertEquals("Address.proto", ProtoGenerator.protoFileFor("Address"));
    }
}
