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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Types whose names clash ignoring case (FareDetailDTO / FareDetailDto) or exactly (Status in two packages)
 * would overwrite each other's .proto file on case-insensitive file systems, so they are prefixed with
 * their package. Everything referencing them must follow.
 */
class ProtoGeneratorNameClashTest {

    private static CompilationUnit parse(String source) {
        return ProtoGeneratorEnumPrefixTest.parse(source);
    }

    private static List<EnumDeclaration> enums(List<CompilationUnit> cus) {
        List<EnumDeclaration> enums = new ArrayList<>();
        cus.forEach(cu -> enums.addAll(cu.findAll(EnumDeclaration.class)));
        return enums;
    }

    private static String message(ProtoGenerator generator, CompilationUnit cu, List<EnumDeclaration> allEnums) {
        List<EnumDeclaration> nested = cu.getPrimaryType().orElseThrow().findAll(EnumDeclaration.class);
        return generator.generateMessageWithNestedEnums(cu, nested, allEnums);
    }

    @Test
    void caseOnlyClashIsPrefixedWithPackageAndReferencesFollow() {
        CompilationUnit upper = parse("""
                package com.example.catalog;
                public class PriceDetailDTO { private String sku; }
                """);
        CompilationUnit lower = parse("""
                package com.example.billing;
                public class PriceDetailDto { private String invoiceId; public enum Kind { CHARGE, REFUND } }
                """);
        CompilationUnit summary = parse("""
                package com.example;
                import com.example.billing.PriceDetailDto;
                import com.example.catalog.PriceDetailDTO;
                import java.util.List;
                public class Summary {
                    private PriceDetailDTO catalogPrice;
                    private List<PriceDetailDto> billed;
                    private PriceDetailDto.Kind lastKind;
                    private PriceDetailDto[] history;
                }
                """);
        List<CompilationUnit> cus = List.of(upper, lower, summary);
        ProtoGenerator generator = new ProtoGenerator();

        Map<String, String> renamed = generator.registerTypes(cus);

        assertEquals(Map.of(
                "com.example.catalog.PriceDetailDTO", "CatalogPriceDetailDTO",
                "com.example.billing.PriceDetailDto", "BillingPriceDetailDto"), renamed);
        List<EnumDeclaration> allEnums = enums(cus);
        assertTrue(message(generator, upper, allEnums).startsWith("message CatalogPriceDetailDTO {"));
        assertTrue(message(generator, lower, allEnums).startsWith("message BillingPriceDetailDto {"));

        String summaryMessage = message(generator, summary, allEnums);
        assertTrue(summaryMessage.startsWith("message Summary {"), "types without a clash keep their name");
        assertTrue(summaryMessage.contains("  CatalogPriceDetailDTO catalogPrice = 1;"));
        assertTrue(summaryMessage.contains("  repeated BillingPriceDetailDto billed = 2;"));
        assertTrue(summaryMessage.contains("  BillingPriceDetailDto.Kind lastKind = 3;"));
        assertTrue(summaryMessage.contains("  repeated BillingPriceDetailDto history = 4;"));
        assertEquals(Set.of("CatalogPriceDetailDTO.proto", "BillingPriceDetailDto.proto"),
                generator.getImports(summary, allEnums));
    }

    @Test
    void noClashMeansNoRename() {
        CompilationUnit a = parse("package com.example.a; public class Order { private String id; }");
        CompilationUnit b = parse("package com.example.b; public class Invoice { private Order order; }");
        ProtoGenerator generator = new ProtoGenerator();

        assertTrue(generator.registerTypes(List.of(a, b)).isEmpty());
        assertTrue(message(generator, b, List.of()).contains("  Order order = 1;"));
        assertEquals(Set.of("Order.proto"), generator.getImports(b, List.of()));
    }

    @Test
    void identicalNamesInDifferentPackagesAreDisambiguatedByImports() {
        CompilationUnit a = parse("package com.example.shipping; public class Status { private String code; }");
        CompilationUnit b = parse("package com.example.payment; public class Status { private String code; }");
        CompilationUnit user = parse("""
                package com.example;
                import com.example.payment.Status;
                public class Order { private Status status; }
                """);
        ProtoGenerator generator = new ProtoGenerator();

        generator.registerTypes(List.of(a, b, user));

        assertTrue(message(generator, user, List.of()).contains("  PaymentStatus status = 1;"));
        assertEquals(Set.of("PaymentStatus.proto"), generator.getImports(user, List.of()));
    }

    @Test
    void sameLastPackageSegmentUsesMoreSegments() {
        CompilationUnit a = parse("package com.alpha.common; public class Status {}");
        CompilationUnit b = parse("package com.beta.common; public class STATUS {}");
        ProtoGenerator generator = new ProtoGenerator();

        assertEquals(Map.of(
                "com.alpha.common.Status", "AlphaCommonStatus",
                "com.beta.common.STATUS", "BetaCommonSTATUS"), generator.registerTypes(List.of(a, b)));
    }

    @Test
    void prefixedNameThatIsAlreadyTakenUsesMoreSegments() {
        CompilationUnit a = parse("package com.a.pricing; public class Fare {}");
        CompilationUnit b = parse("package com.b.booking; public class Fare {}");
        CompilationUnit existing = parse("package com.c; public class PricingFare {}");
        ProtoGenerator generator = new ProtoGenerator();

        Map<String, String> renamed = generator.registerTypes(List.of(a, b, existing));

        assertEquals("APricingFare", renamed.get("com.a.pricing.Fare"));
        assertEquals("BBookingFare", renamed.get("com.b.booking.Fare"));
        assertEquals("PricingFare", generator.protoName(existing.getPrimaryType().orElseThrow()));
    }

    @Test
    void caseOnlyClashInTheSamePackageFallsBackToNumericSuffix() {
        CompilationUnit a = parse("package com.example.dto; public class FareDetailDTO {}");
        CompilationUnit b = parse("package com.example.dto; public class FareDetailDto {}");
        ProtoGenerator generator = new ProtoGenerator();

        Map<String, String> renamed = generator.registerTypes(List.of(a, b));

        assertEquals("DtoFareDetailDTO1", renamed.get("com.example.dto.FareDetailDTO"));
        assertEquals("DtoFareDetailDto2", renamed.get("com.example.dto.FareDetailDto"));
    }

    @Test
    void clashingTopLevelEnumsAreRenamedIncludingValuePrefixes() {
        CompilationUnit a = parse("package com.example.shipping; public enum Status { OPEN, CLOSED }");
        CompilationUnit b = parse("package com.example.payment; public enum Status { OPEN, PAID }");
        CompilationUnit user = parse("""
                package com.example;
                import com.example.shipping.Status;
                public class Parcel { private Status status; }
                """);
        List<CompilationUnit> cus = List.of(a, b, user);
        ProtoGenerator generator = new ProtoGenerator(true);
        generator.registerTypes(cus);
        List<EnumDeclaration> allEnums = enums(cus);

        String shipping = generator.generateEnum(a.findFirst(EnumDeclaration.class).orElseThrow());
        assertTrue(shipping.startsWith("enum ShippingStatus {"));
        assertTrue(shipping.contains("  SHIPPING_STATUS_OPEN = 0;"), "value prefixes use the proto name so they stay unique");
        assertTrue(message(generator, user, allEnums).contains("  ShippingStatus status = 1;"));
        assertEquals(Set.of("ShippingStatus.proto"), generator.getImports(user, allEnums));
    }

    @Test
    void packagePrefixIsPascalCase() {
        CompilationUnit cu = parse("package com.example.fare_calc; public class X {}");
        assertEquals("FareCalc", ProtoGenerator.packagePrefix(cu.getPrimaryType().orElseThrow(), 1));
        assertEquals("ExampleFareCalc", ProtoGenerator.packagePrefix(cu.getPrimaryType().orElseThrow(), 2));
    }
}
