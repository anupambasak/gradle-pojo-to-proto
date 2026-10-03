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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code nameSuffix}, {@code usePojoPackages} and {@code javaPackageSuffix} options. */
class ProtoGeneratorPackageAndSuffixTest {

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

    private final CompilationUnit address = parse("""
            package com.example.dtos;
            public class Address { private String city; }
            """);
    private final CompilationUnit status = parse("""
            package com.example.enums;
            public enum OrderStatus { ACTIVE, CANCELLED }
            """);
    private final CompilationUnit constants = parse("""
            package com.example.enums;
            public interface AppConstants { enum TxnType { BUY, SELL } }
            """);
    private final CompilationUnit order = parse("""
            package com.example.dtos;
            import com.example.enums.OrderStatus;
            import com.example.enums.AppConstants;
            import java.util.List;
            import java.util.Map;
            public class Order {
                private Address shipTo;
                private OrderStatus status;
                private AppConstants.TxnType txnType;
                private Map<String, List<Address>> addressesByCity;
                private Map<String, List<OrderStatus>> statusesByDay;
                private Kind kind;
                public enum Kind { RETAIL, WHOLESALE }
            }
            """);
    private final List<CompilationUnit> cus = List.of(address, status, constants, order);

    @Test
    void nameSuffixIsAppendedToTopLevelMessagesEnumsAndFiles() {
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options().nameSuffix("Proto").prefixEnumNames(true));
        generator.registerTypes(cus);
        List<EnumDeclaration> allEnums = enums(cus);

        assertEquals("AddressProto", generator.protoName(address.getPrimaryType().orElseThrow()));
        assertEquals("AddressProto.proto", generator.protoFile(address.getPrimaryType().orElseThrow()));
        assertEquals("OrderStatusProto.proto", generator.protoFile(status.getPrimaryType().orElseThrow()));

        String proto = message(generator, order, allEnums);
        assertTrue(proto.startsWith("message OrderProto {\n"), proto);
        assertTrue(proto.contains("  AddressProto shipTo = 1;"), proto);
        assertTrue(proto.contains("  OrderStatusProto status = 2;"), proto);
        assertTrue(proto.contains("  AppConstantsProto.TxnType txnType = 3;"), proto);
        assertTrue(proto.contains("  map<string, AddressListProto> addressesByCity = 4;"), proto);
        assertTrue(proto.contains("  map<string, OrderStatusListProto> statusesByDay = 5;"), proto);
        // Nested enums live inside the message and keep their name
        assertTrue(proto.contains("enum Kind {\n  KIND_RETAIL = 0;"), proto);
        assertTrue(proto.contains("  Kind kind = 6;"), proto);

        assertEquals(Set.of("AddressProto.proto", "AppConstantsProto.proto", "OrderStatusProto.proto",
                "AddressListProto.proto", "OrderStatusListProto.proto"), generator.getImports(order, allEnums));

        // Enum value prefixes are based on the name without the suffix
        String enumProto = generator.generateEnum(status.findFirst(EnumDeclaration.class).orElseThrow());
        assertTrue(enumProto.startsWith("enum OrderStatusProto {\n  ORDER_STATUS_ACTIVE = 0;"), enumProto);
    }

    @Test
    void wrapperNamesCarryTheSuffixOnce() {
        CompilationUnit nested = parse("""
                package com.example.dtos;
                import java.util.List;
                public class Pages { private List<List<List<Address>>> pages; private List<List<String>> lines; }
                """);
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options().nameSuffix("Proto"));
        generator.registerTypes(List.of(address, nested));
        message(generator, nested, List.of());

        Set<String> names = generator.getTopLevelWrappers().stream()
                .map(ProtoGenerator.WrapperMessage::getName).collect(Collectors.toSet());
        assertEquals(Set.of("AddressListProto", "AddressListListProto", "StringListProto"), names);
        assertEquals("AddressListList", ProtoGenerator.wrapperBaseName("AddressListProto", "Proto"));
    }

    @Test
    void renamedClashesAlsoGetTheSuffix() {
        CompilationUnit upper = parse("package com.example.catalog;\npublic class PriceDetailDTO { private String sku; }");
        CompilationUnit lower = parse("package com.example.billing;\npublic class PriceDetailDto { private String id; }");
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options().nameSuffix("Proto"));

        assertEquals(Map.of(
                "com.example.catalog.PriceDetailDTO", "CatalogPriceDetailDTOProto",
                "com.example.billing.PriceDetailDto", "BillingPriceDetailDtoProto"), generator.registerTypes(List.of(upper, lower)));
    }

    @Test
    void pojoPackagesDriveProtoPackageFilePathsImportsAndReferences() {
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options()
                .usePojoPackages(true).nameSuffix("Proto"));
        generator.registerTypes(cus);
        List<EnumDeclaration> allEnums = enums(cus);

        assertEquals("com.example.dtos", generator.protoPackage(order));
        assertEquals("com.example.enums", generator.protoPackage(status.getPrimaryType().orElseThrow()));
        assertEquals("com/example/dtos/AddressProto.proto", generator.protoFile(address.getPrimaryType().orElseThrow()));
        assertEquals("com/example/enums/OrderStatusProto.proto", generator.protoFile(status.getPrimaryType().orElseThrow()));

        String proto = message(generator, order, allEnums);
        // Same package: plain name. Other package: fully qualified.
        assertTrue(proto.contains("  AddressProto shipTo = 1;"), proto);
        assertTrue(proto.contains("  com.example.enums.OrderStatusProto status = 2;"), proto);
        assertTrue(proto.contains("  com.example.enums.AppConstantsProto.TxnType txnType = 3;"), proto);
        assertTrue(proto.contains("  map<string, AddressListProto> addressesByCity = 4;"), proto);
        assertTrue(proto.contains("  map<string, OrderStatusListProto> statusesByDay = 5;"), proto);
        assertTrue(proto.contains("  Kind kind = 6;"), proto);

        assertEquals(Set.of(
                "com/example/dtos/AddressProto.proto",
                "com/example/enums/AppConstantsProto.proto",
                "com/example/enums/OrderStatusProto.proto",
                "com/example/dtos/AddressListProto.proto",
                "com/example/dtos/OrderStatusListProto.proto"), generator.getImports(order, allEnums));

        // Wrappers live in the package of the message using them; their element reference is valid there
        ProtoGenerator.WrapperMessage statusList = generator.getTopLevelWrappers().stream()
                .filter(w -> w.getName().equals("OrderStatusListProto")).findFirst().orElseThrow();
        assertEquals("com.example.dtos", statusList.getProtoPackage());
        assertEquals("com/example/dtos/OrderStatusListProto.proto", statusList.getFile());
        assertEquals(Set.of("com/example/enums/OrderStatusProto.proto"), statusList.getImports());
        assertEquals("message OrderStatusListProto {\n  repeated com.example.enums.OrderStatusProto items = 1;\n}\n\n",
                generator.generateWrapperMessage(statusList));
    }

    @Test
    void withPojoPackagesOnlyTypesInTheSamePackageClash() {
        CompilationUnit catalogStatus = parse("package com.example.catalog;\npublic enum Status { A }");
        CompilationUnit billingStatus = parse("package com.example.billing;\npublic enum Status { B }");
        CompilationUnit user = parse("""
                package com.example;
                import com.example.billing.Status;
                public class User { private Status status; private com.example.catalog.Status catalogStatus; }
                """);
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options().usePojoPackages(true));
        List<CompilationUnit> all = List.of(catalogStatus, billingStatus, user);

        assertEquals(Map.of(), generator.registerTypes(all), "different packages and directories: no rename");
        String proto = message(generator, user, enums(all));
        assertTrue(proto.contains("  com.example.billing.Status status = 1;"), proto);
        assertTrue(proto.contains("  com.example.catalog.Status catalogStatus = 2;"), proto);
    }

    @Test
    void withPojoPackagesTheSharedPackageIsIgnored() {
        CompilationUnit loose = parse("public class Loose { private String x; }");
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options().usePojoPackages(true).sharedPackage("com.shared"));
        generator.registerTypes(List.of(loose, address));

        assertEquals("com.example.dtos", generator.protoPackage(address));
        // A class in the default package has no package to mirror
        assertEquals("", generator.protoPackage(loose));
        assertEquals("Loose.proto", generator.protoFile(loose.getPrimaryType().orElseThrow()));
    }

    @Test
    void flatLayoutUsesTheSharedPackageForEveryType() {
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options().sharedPackage("com.shared"));
        generator.registerTypes(cus);

        assertEquals("com.shared", generator.protoPackage(order));
        assertEquals("com.shared", generator.protoPackage(status.getPrimaryType().orElseThrow()));
        assertEquals("Address.proto", generator.protoFile(address.getPrimaryType().orElseThrow()));
        assertTrue(message(generator, order, enums(cus)).contains("  OrderStatus status = 2;"));
    }

    @Test
    void withPojoPackagesJavaPackageSuffixIsPartOfProtoPackageAndDirectory() {
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options()
                .usePojoPackages(true).nameSuffix("Proto").javaPackageSuffix(".proto"));
        generator.registerTypes(cus);
        List<EnumDeclaration> allEnums = enums(cus);

        assertEquals("com.example.dtos.proto", generator.protoPackage(order));
        assertEquals("com/example/dtos/proto/AddressProto.proto", generator.protoFile(address.getPrimaryType().orElseThrow()));
        assertEquals("com/example/enums/proto/OrderStatusProto.proto", generator.protoFile(status.getPrimaryType().orElseThrow()));

        String header = generator.generateHeader(generator.protoPackage(order), Set.of());
        assertTrue(header.contains("package com.example.dtos.proto;\n"), header);
        assertTrue(header.contains("option java_package = \"com.example.dtos.proto\";\n"), header);

        String proto = message(generator, order, allEnums);
        assertTrue(proto.contains("  AddressProto shipTo = 1;"), proto);
        assertTrue(proto.contains("  com.example.enums.proto.OrderStatusProto status = 2;"), proto);
        assertTrue(proto.contains("  com.example.enums.proto.AppConstantsProto.TxnType txnType = 3;"), proto);
        assertTrue(generator.getImports(order, allEnums).contains("com/example/enums/proto/OrderStatusProto.proto"));

        ProtoGenerator.WrapperMessage statusList = generator.getTopLevelWrappers().stream()
                .filter(w -> w.getName().equals("OrderStatusListProto")).findFirst().orElseThrow();
        assertEquals("com.example.dtos.proto", statusList.getProtoPackage());
        assertEquals("com/example/dtos/proto/OrderStatusListProto.proto", statusList.getFile());
    }

    @Test
    void withSharedPackageJavaPackageSuffixOnlyAffectsJavaPackage() {
        ProtoGenerator generator = new ProtoGenerator(new ProtoGenerator.Options().javaPackageSuffix(".proto"));
        String header = generator.generateHeader("com.example.dtos", Set.of());

        assertTrue(header.contains("package com.example.dtos;\n"), header);
        assertTrue(header.contains("option java_package = \"com.example.dtos.proto\";\n"), header);
    }

    @Test
    void defaultsKeepTheFlatLayout() {
        ProtoGenerator generator = new ProtoGenerator();
        generator.registerTypes(cus);

        assertEquals("Address.proto", generator.protoFile(address.getPrimaryType().orElseThrow()));
        assertEquals("", generator.protoPackage(order));
        String proto = message(generator, order, enums(cus));
        assertTrue(proto.contains("  OrderStatus status = 2;"), proto);
        assertTrue(proto.contains("  AppConstants.TxnType txnType = 3;"), proto);
    }
}
