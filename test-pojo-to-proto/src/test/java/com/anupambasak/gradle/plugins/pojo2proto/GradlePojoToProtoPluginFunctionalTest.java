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

package com.anupambasak.gradle.plugins.pojo2proto;

import io.github.anupambasak.gradle.dtos.Address;
import io.github.anupambasak.gradle.dtos.ArrayPojo;
import io.github.anupambasak.gradle.dtos.PersonPojo;
import io.github.anupambasak.gradle.dtos.SessionPojo;
import io.github.anupambasak.gradle.dtos.TimePojo;
import io.github.anupambasak.gradle.testenums.Conts;
import io.github.anupambasak.gradle.testenums.EnumPojo;
import io.github.anupambasak.gradle.testenums.AppConstants;
import io.github.anupambasak.gradle.testenums.TestEnum;
import io.github.anupambasak.gradle.dtos.billing.proto.PriceDetailDtoProto;
import io.github.anupambasak.gradle.dtos.catalog.proto.PriceDetailDTOProto;
import io.github.anupambasak.gradle.dtos.proto.AddressListProto;
import io.github.anupambasak.gradle.dtos.proto.AddressProto;
import io.github.anupambasak.gradle.dtos.proto.ApiResponseProto;
import io.github.anupambasak.gradle.dtos.proto.ArrayPojoProto;
import io.github.anupambasak.gradle.dtos.proto.CollectionPojoProto;
import io.github.anupambasak.gradle.dtos.proto.GroupedPojoProto;
import io.github.anupambasak.gradle.dtos.proto.MapPojoProto;
import io.github.anupambasak.gradle.dtos.proto.PersonPojoProto;
import io.github.anupambasak.gradle.dtos.proto.PriceSummaryPojoProto;
import io.github.anupambasak.gradle.dtos.proto.ResponseEnvelopeProto;
import io.github.anupambasak.gradle.dtos.proto.SessionPojoProto;
import io.github.anupambasak.gradle.dtos.proto.StringListProto;
import io.github.anupambasak.gradle.dtos.proto.TimePojoProto;
import io.github.anupambasak.gradle.testenums.proto.AppConstantsProto;
import io.github.anupambasak.gradle.testenums.proto.ContsProto;
import io.github.anupambasak.gradle.testenums.proto.EnumPojoProto;
import io.github.anupambasak.gradle.testenums.proto.TestEnumProto;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.github.anupambasak.gradle.dtos.MapPojo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GradlePojoToProtoPluginFunctionalTest {

    private static final String protoDir = "src/main/proto";

    /**
     * Generated files follow the POJO package plus javaPackageSuffix, matching their package and java_package:
     * io.github.anupambasak.gradle.dtos.Address -> io/github/anupambasak/gradle/dtos/proto/AddressProto.proto
     */
    private static Path protoFile(String pojoPackageDir, String file) {
        return Path.of(protoDir, "io/github/anupambasak/gradle/" + pojoPackageDir + "/proto", file);
    }

    private static Path dtos(String file) {
        return protoFile("dtos", file);
    }

    private static Path enums(String file) {
        return protoFile("testenums", file);
    }


    @Test
    void verifyPersonPojoProtoContent() throws IOException {
        Path personPojoProtoPath = dtos("PersonPojoProto.proto");
        assertTrue(Files.exists(personPojoProtoPath), "PersonPojoProto.proto should be generated");

        String personPojoProtoContent = Files.readString(personPojoProtoPath);
        assertTrue(personPojoProtoContent.contains("syntax = \"proto3\";"));
        assertTrue(personPojoProtoContent.contains("package io.github.anupambasak.gradle.dtos.proto;"));
        assertTrue(personPojoProtoContent.contains("option java_package = \"io.github.anupambasak.gradle.dtos.proto\";"));
        assertTrue(personPojoProtoContent.contains("option java_multiple_files = true;"));
        assertTrue(personPojoProtoContent.contains("import \"io/github/anupambasak/gradle/dtos/proto/AddressProto.proto\";"));
        assertTrue(personPojoProtoContent.contains("import \"google/protobuf/timestamp.proto\";"));
        assertTrue(personPojoProtoContent.contains("message PersonPojoProto {"));
        assertTrue(personPojoProtoContent.contains("  string name = 1;"));
        assertTrue(personPojoProtoContent.contains("  int32 age = 2;"));
        assertTrue(personPojoProtoContent.contains("  int32 weight = 3;"));
        assertFalse(personPojoProtoContent.contains("import \"short.proto\";"), "Java primitives must not be imported");
        assertTrue(personPojoProtoContent.contains("  AddressProto address = 4;"));
        assertTrue(personPojoProtoContent.contains("  repeated AddressProto previousAddresses = 5;"));
        assertTrue(personPojoProtoContent.contains("  repeated AddressProto addressesHome = 6;"));
        assertTrue(personPojoProtoContent.contains("  google.protobuf.Timestamp createdAt = 7;"));
        assertTrue(personPojoProtoContent.contains("  google.protobuf.Timestamp dob = 8;"));
    }

    @Test
    void verifyTimePojoProtoContent() throws IOException {
        Path timePojoProtoPath = dtos("TimePojoProto.proto");
        assertTrue(Files.exists(timePojoProtoPath), "TimePojoProto.proto should be generated");

        String timePojoProtoContent = Files.readString(timePojoProtoPath);
        assertTrue(timePojoProtoContent.contains("syntax = \"proto3\";"));
        assertTrue(timePojoProtoContent.contains("package io.github.anupambasak.gradle.dtos.proto;"));
        assertTrue(timePojoProtoContent.contains("option java_package = \"io.github.anupambasak.gradle.dtos.proto\";"));
        assertTrue(timePojoProtoContent.contains("option java_multiple_files = true;"));
        assertTrue(timePojoProtoContent.contains("import \"google/protobuf/duration.proto\";"));
        assertTrue(timePojoProtoContent.contains("import \"google/protobuf/timestamp.proto\";"));
        assertTrue(timePojoProtoContent.contains("import \"google/type/date.proto\";"));
        assertTrue(timePojoProtoContent.contains("import \"google/type/timeofday.proto\";"));
        assertTrue(timePojoProtoContent.contains("message TimePojoProto {"));
        assertTrue(timePojoProtoContent.contains("  google.protobuf.Timestamp instant = 1;"));
        assertTrue(timePojoProtoContent.contains("  google.protobuf.Timestamp zonedDateTime = 2;"));
        assertTrue(timePojoProtoContent.contains("  google.protobuf.Timestamp localDateTime = 3;"));
        assertTrue(timePojoProtoContent.contains("  google.type.Date localDate = 4;"));
        assertTrue(timePojoProtoContent.contains("  google.type.TimeOfDay localTime = 5;"));
        assertTrue(timePojoProtoContent.contains("  google.protobuf.Duration duration = 6;"));
        assertTrue(timePojoProtoContent.contains("  string period = 7;"));
        assertTrue(timePojoProtoContent.contains("  repeated google.protobuf.Timestamp instants = 8;"));
        assertTrue(timePojoProtoContent.contains("  repeated google.protobuf.Timestamp zonedDateTimes = 9;"));
        assertTrue(timePojoProtoContent.contains("  repeated google.protobuf.Timestamp localDateTimes = 10;"));
        assertTrue(timePojoProtoContent.contains("  repeated google.type.Date localDates = 11;"));
        assertTrue(timePojoProtoContent.contains("  repeated google.type.TimeOfDay localTimes = 12;"));
        assertTrue(timePojoProtoContent.contains("  repeated google.protobuf.Duration durations = 13;"));
        assertTrue(timePojoProtoContent.contains("  repeated string periods = 14;"));
        assertTrue(timePojoProtoContent.contains("  google.protobuf.Timestamp date = 15;"));
        assertTrue(timePojoProtoContent.contains("  repeated google.protobuf.Timestamp dates = 16;"));
        assertTrue(timePojoProtoContent.contains("  google.protobuf.Timestamp qualifiedDate = 17;"));
        assertFalse(timePojoProtoContent.contains("import \"Date.proto\";"), "java.util.Date must map to Timestamp, not a message");
    }

        @Test
        void verifyMapPojoProtoContent() throws IOException {
            Path mapPojoProtoPath = dtos("MapPojoProto.proto");
            assertTrue(Files.exists(mapPojoProtoPath), "MapPojoProto.proto should be generated");
            String mapPojoProtoContent = Files.readString(mapPojoProtoPath);
            assertTrue(mapPojoProtoContent.contains("syntax = \"proto3\";"));
            assertTrue(mapPojoProtoContent.contains("package io.github.anupambasak.gradle.dtos.proto;"));
            assertTrue(mapPojoProtoContent.contains("option java_package = \"io.github.anupambasak.gradle.dtos.proto\";"));
            assertTrue(mapPojoProtoContent.contains("option java_multiple_files = true;"));
            assertTrue(mapPojoProtoContent.contains("import \"io/github/anupambasak/gradle/dtos/proto/AddressProto.proto\";"));
            assertTrue(mapPojoProtoContent.contains("message MapPojoProto {"));
            assertTrue(mapPojoProtoContent.contains("  map<string, int32> simpleMap = 1;"));
            assertTrue(mapPojoProtoContent.contains("  map<string, AddressProto> complexMap = 2;"));

        }

    

        @Test

        void verifyContsProtoContent() throws IOException {
            Path contsProtoPath = enums("ContsProto.proto");
            assertTrue(Files.exists(contsProtoPath), "ContsProto.proto should be generated");
            String contsProtoContent = Files.readString(contsProtoPath);
            assertTrue(contsProtoContent.contains("syntax = \"proto3\";"));
            assertTrue(contsProtoContent.contains("package io.github.anupambasak.gradle.testenums.proto;"));
            assertTrue(contsProtoContent.contains("option java_package = \"io.github.anupambasak.gradle.testenums.proto\";"));
            assertTrue(contsProtoContent.contains("option java_multiple_files = true;"));
            assertFalse(contsProtoContent.contains("import \"BerthType.proto\";")); // No incorrect import
            assertTrue(contsProtoContent.contains("message ContsProto {"));
            assertTrue(contsProtoContent.contains("enum b {"));
            assertTrue(contsProtoContent.contains("  B_c = 0;"));
            assertTrue(contsProtoContent.contains("  B_d = 1;"));
            assertTrue(contsProtoContent.contains("  B_e = 2;"));
            assertTrue(contsProtoContent.contains("  B_f = 3;"));
            assertTrue(contsProtoContent.contains("  B_g = 4;"));
            assertTrue(contsProtoContent.contains("  B_h = 5;"));
            assertTrue(contsProtoContent.contains("  B_i = 6;"));
            assertTrue(contsProtoContent.contains("  B_j = 7;"));
            assertTrue(contsProtoContent.contains("  B_k = 8;"));
            assertTrue(contsProtoContent.contains("  B_l = 9;"));
            assertTrue(contsProtoContent.contains("  B_m = 10;"));
            assertTrue(contsProtoContent.contains("  B_n = 11;"));
            assertTrue(contsProtoContent.contains("  B_o = 12;"));
            assertFalse(contsProtoContent.contains("  c = 0;"), "nested enum values should be prefixed when prefixEnumNames = true");
        }


    @Test
    void verifyEnumPojosProtoContent() throws IOException {
        Path enumPojoProtoPath = enums("EnumPojoProto.proto");
        assertTrue(Files.exists(enumPojoProtoPath), "EnumPojoProto.proto should be generated");

        String enumPojoProtoContent = Files.readString(enumPojoProtoPath);
        assertTrue(enumPojoProtoContent.contains("syntax = \"proto3\";"));
        assertTrue(enumPojoProtoContent.contains("package io.github.anupambasak.gradle.testenums.proto;"));
        assertTrue(enumPojoProtoContent.contains("option java_package = \"io.github.anupambasak.gradle.testenums.proto\";"));
        assertTrue(enumPojoProtoContent.contains("option java_multiple_files = true;"));
        assertTrue(enumPojoProtoContent.contains("import \"io/github/anupambasak/gradle/testenums/proto/ContsProto.proto\";"));
        assertTrue(enumPojoProtoContent.contains("import \"io/github/anupambasak/gradle/testenums/proto/TestEnumProto.proto\";"));
        assertTrue(enumPojoProtoContent.contains("message EnumPojoProto {"));
        assertTrue(enumPojoProtoContent.contains("  TestEnumProto testEnum = 1;"));
        assertTrue(enumPojoProtoContent.contains("  ContsProto.b berthType = 2;"));

        Path testEnumProtoPath = enums("TestEnumProto.proto");
        assertTrue(Files.exists(testEnumProtoPath), "TestEnumProto.proto should be generated");

        String testEnumProtoContent = Files.readString(testEnumProtoPath);
        assertTrue(testEnumProtoContent.contains("syntax = \"proto3\";"));
        assertTrue(testEnumProtoContent.contains("package io.github.anupambasak.gradle.testenums.proto;"));
        assertTrue(testEnumProtoContent.contains("option java_package = \"io.github.anupambasak.gradle.testenums.proto\";"));
        assertTrue(testEnumProtoContent.contains("option java_multiple_files = true;"));
        assertTrue(testEnumProtoContent.contains("enum TestEnumProto {"));
        assertTrue(testEnumProtoContent.contains("  TEST_ENUM_VALUE1 = 0;"));
        assertTrue(testEnumProtoContent.contains("  TEST_ENUM_VALUE2 = 1;"));
        assertTrue(testEnumProtoContent.contains("  TEST_ENUM_VALUE3 = 2;"));
        assertFalse(testEnumProtoContent.contains("  VALUE1 = 0;"), "enum values should be prefixed when prefixEnumNames = true");
    }

    @Test
    void verifyProtoFromPojo() {
        // Create Address POJO
        Address addressPojo = new Address();
        addressPojo.setStreet("123 Main St");
        addressPojo.setCity("Anytown");
        addressPojo.setZipCode(12345);

        // Create PersonPojo
        PersonPojo personPojo = new PersonPojo();
        personPojo.setName("John Doe");
        personPojo.setAge(30);
        personPojo.setWeight((short) 70);
        personPojo.setAddress(addressPojo);
        personPojo.setPreviousAddresses(Collections.singletonList(addressPojo));
        personPojo.setCreatedAt(Instant.now());
        personPojo.setDob(LocalDateTime.of(1990, 1, 1, 0, 0));

        // Create Proto from PersonPojo
        AddressProto addressProto = AddressProto.newBuilder()
                .setStreet(addressPojo.getStreet())
                .setCity(addressPojo.getCity())
                .setZipCode(addressPojo.getZipCode())
                .build();

        PersonPojoProto personProto = PersonPojoProto.newBuilder()
                .setName(personPojo.getName())
                .setAge(personPojo.getAge())
                .setWeight(personPojo.getWeight())
                .setAddress(addressProto)
                .addPreviousAddresses(addressProto)
                .setCreatedAt(Timestamps.fromMillis(personPojo.getCreatedAt().toEpochMilli()))
                .setDob(Timestamp.newBuilder().setSeconds(personPojo.getDob().toEpochSecond(ZoneOffset.UTC)).build())
                .build();

        // Assert values
        assertEquals(personPojo.getName(), personProto.getName());
        assertEquals(personPojo.getAge(), personProto.getAge());
        assertEquals(personPojo.getWeight(), personProto.getWeight());
        assertEquals(addressPojo.getStreet(), personProto.getAddress().getStreet());
        assertEquals(addressPojo.getCity(), personProto.getAddress().getCity());
        assertEquals(addressPojo.getZipCode(), personProto.getAddress().getZipCode());
        assertEquals(1, personProto.getPreviousAddressesCount());
        assertEquals(addressPojo.getStreet(), personProto.getPreviousAddresses(0).getStreet());
        assertEquals(personPojo.getCreatedAt().getEpochSecond(), personProto.getCreatedAt().getSeconds());
        assertEquals(personPojo.getDob().toEpochSecond(ZoneOffset.UTC), personProto.getDob().getSeconds());

        assertNotNull(personProto.toByteArray());
    }

    @Test
    void verifyProtoFromTimePojo() {
        // Create TimePojo
        TimePojo timePojo = new TimePojo();
        timePojo.setInstant(Instant.now());
        timePojo.setZonedDateTime(ZonedDateTime.now());
        timePojo.setLocalDateTime(LocalDateTime.now());
        timePojo.setLocalDate(LocalDate.now());
        timePojo.setLocalTime(LocalTime.now());
        timePojo.setDuration(java.time.Duration.ofHours(1));
        timePojo.setPeriod(java.time.Period.ofDays(1));
        timePojo.setInstants(Collections.singletonList(Instant.now()));
        timePojo.setZonedDateTimes(Collections.singletonList(ZonedDateTime.now()));
        timePojo.setLocalDateTimes(Collections.singletonList(LocalDateTime.now()));
        timePojo.setLocalDates(Collections.singletonList(LocalDate.now()));
        timePojo.setLocalTimes(Collections.singletonList(LocalTime.now()));
        timePojo.setDurations(Collections.singletonList(java.time.Duration.ofHours(1)));
        timePojo.setPeriods(Collections.singletonList(java.time.Period.ofDays(1)));
        timePojo.setDate(new java.util.Date());
        timePojo.setDates(Collections.singletonList(new java.util.Date()));

        // Create Proto from TimePojo
        TimePojoProto timeProto = TimePojoProto.newBuilder()
                .setInstant(Timestamps.fromMillis(timePojo.getInstant().toEpochMilli()))
                .setZonedDateTime(Timestamps.fromMillis(timePojo.getZonedDateTime().toInstant().toEpochMilli()))
                .setLocalDateTime(Timestamp.newBuilder().setSeconds(timePojo.getLocalDateTime().toEpochSecond(ZoneOffset.UTC)).build())
                .setLocalDate(com.google.type.Date.newBuilder().setYear(timePojo.getLocalDate().getYear()).setMonth(timePojo.getLocalDate().getMonthValue()).setDay(timePojo.getLocalDate().getDayOfMonth()).build())
                .setLocalTime(com.google.type.TimeOfDay.newBuilder().setHours(timePojo.getLocalTime().getHour()).setMinutes(timePojo.getLocalTime().getMinute()).setSeconds(timePojo.getLocalTime().getSecond()).setNanos(timePojo.getLocalTime().getNano()).build())
                .setDuration(com.google.protobuf.Duration.newBuilder().setSeconds(timePojo.getDuration().getSeconds()).setNanos(timePojo.getDuration().getNano()).build())
                .setPeriod(timePojo.getPeriod().toString())
                .addInstants(Timestamps.fromMillis(timePojo.getInstants().get(0).toEpochMilli()))
                .addZonedDateTimes(Timestamps.fromMillis(timePojo.getZonedDateTimes().get(0).toInstant().toEpochMilli()))
                .addLocalDateTimes(Timestamp.newBuilder().setSeconds(timePojo.getLocalDateTimes().get(0).toEpochSecond(ZoneOffset.UTC)).build())
                .addLocalDates(com.google.type.Date.newBuilder().setYear(timePojo.getLocalDates().get(0).getYear()).setMonth(timePojo.getLocalDates().get(0).getMonthValue()).setDay(timePojo.getLocalDates().get(0).getDayOfMonth()).build())
                .addLocalTimes(com.google.type.TimeOfDay.newBuilder().setHours(timePojo.getLocalTimes().get(0).getHour()).setMinutes(timePojo.getLocalTimes().get(0).getMinute()).setSeconds(timePojo.getLocalTimes().get(0).getSecond()).setNanos(timePojo.getLocalTimes().get(0).getNano()).build())
                .addDurations(com.google.protobuf.Duration.newBuilder().setSeconds(timePojo.getDurations().get(0).getSeconds()).setNanos(timePojo.getDurations().get(0).getNano()).build())
                .addPeriods(timePojo.getPeriods().get(0).toString())
                .setDate(Timestamps.fromMillis(timePojo.getDate().getTime()))
                .addDates(Timestamps.fromMillis(timePojo.getDates().get(0).getTime()))
                .build();

        // Assert values
        assertEquals(timePojo.getInstant().getEpochSecond(), timeProto.getInstant().getSeconds());
        assertEquals(timePojo.getZonedDateTime().toEpochSecond(), timeProto.getZonedDateTime().getSeconds());
        assertEquals(timePojo.getLocalDateTime().toEpochSecond(ZoneOffset.UTC), timeProto.getLocalDateTime().getSeconds());
        assertEquals(timePojo.getLocalDate().getYear(), timeProto.getLocalDate().getYear());
        assertEquals(timePojo.getLocalDate().getMonthValue(), timeProto.getLocalDate().getMonth());
        assertEquals(timePojo.getLocalDate().getDayOfMonth(), timeProto.getLocalDate().getDay());
        assertEquals(timePojo.getLocalTime().getHour(), timeProto.getLocalTime().getHours());
        assertEquals(timePojo.getLocalTime().getMinute(), timeProto.getLocalTime().getMinutes());
        assertEquals(timePojo.getLocalTime().getSecond(), timeProto.getLocalTime().getSeconds());
        assertEquals(timePojo.getLocalTime().getNano(), timeProto.getLocalTime().getNanos());
        assertEquals(timePojo.getDuration().getSeconds(), timeProto.getDuration().getSeconds());
        assertEquals(timePojo.getDuration().getNano(), timeProto.getDuration().getNanos());
        assertEquals(timePojo.getPeriod().toString(), timeProto.getPeriod());
        assertEquals(1, timeProto.getInstantsCount());
        assertEquals(timePojo.getInstants().get(0).getEpochSecond(), timeProto.getInstants(0).getSeconds());
        assertEquals(1, timeProto.getZonedDateTimesCount());
        assertEquals(timePojo.getZonedDateTimes().get(0).toEpochSecond(), timeProto.getZonedDateTimes(0).getSeconds());
        assertEquals(1, timeProto.getLocalDateTimesCount());
        assertEquals(timePojo.getLocalDateTimes().get(0).toEpochSecond(ZoneOffset.UTC), timeProto.getLocalDateTimes(0).getSeconds());
        assertEquals(1, timeProto.getLocalDatesCount());
        assertEquals(timePojo.getLocalDates().get(0).getYear(), timeProto.getLocalDates(0).getYear());
        assertEquals(1, timeProto.getLocalTimesCount());
        assertEquals(timePojo.getLocalTimes().get(0).getHour(), timeProto.getLocalTimes(0).getHours());
        assertEquals(1, timeProto.getDurationsCount());
        assertEquals(timePojo.getDurations().get(0).getSeconds(), timeProto.getDurations(0).getSeconds());
        assertEquals(1, timeProto.getPeriodsCount());
        assertEquals(timePojo.getDate().getTime(), Timestamps.toMillis(timeProto.getDate()));
        assertEquals(1, timeProto.getDatesCount());
        assertEquals(timePojo.getDates().get(0).getTime(), Timestamps.toMillis(timeProto.getDates(0)));
        assertEquals(timePojo.getPeriods().get(0).toString(), timeProto.getPeriods(0));

        assertNotNull(timeProto.toByteArray());
    }

    @Test
    void verifyProtoFromEnumPojo() {
        // Create EnumPojo
        EnumPojo enumPojo = new EnumPojo();
        enumPojo.setTestEnum(TestEnum.VALUE2);
        enumPojo.setBerthType(Conts.b.c);

        // Create Proto from EnumPojo
        EnumPojoProto enumProto = EnumPojoProto.newBuilder()
                .setTestEnum(TestEnumProto.TEST_ENUM_VALUE2)
                .setBerthType(ContsProto.b.B_c)
                .build();

        // Assert values (proto enum values carry the enum-name prefix; ordinals line up with the Java enum)
        assertEquals("TEST_ENUM_" + enumPojo.getTestEnum().name(), enumProto.getTestEnum().name());
        assertEquals("B_" + enumPojo.getBerthType().name(), enumProto.getBerthType().name());
        assertEquals(enumPojo.getTestEnum().ordinal(), enumProto.getTestEnum().getNumber());
        assertEquals(enumPojo.getBerthType().ordinal(), enumProto.getBerthType().getNumber());
    }

    @Test
    void verifyNestedInterfaceEnumsAreReferencedQualified() throws IOException {
        Path sessionProtoPath = dtos("SessionPojoProto.proto");
        assertTrue(Files.exists(sessionProtoPath), "SessionPojoProto.proto should be generated");

        String sessionProto = Files.readString(sessionProtoPath);
        assertTrue(sessionProto.contains("import \"io/github/anupambasak/gradle/testenums/proto/AppConstantsProto.proto\";"));
        assertFalse(sessionProto.contains("import \"io/github/anupambasak/gradle/testenums/proto/TxnTypeProto.proto\";"), "nested enums have no .proto file of their own");
        assertFalse(sessionProto.contains("import \"io/github/anupambasak/gradle/testenums/proto/RecordStatusProto.proto\";"), "nested enums have no .proto file of their own");
        assertTrue(sessionProto.contains("message SessionPojoProto {"));
        assertTrue(sessionProto.contains("  int32 sessionNumber = 1;"));
        assertTrue(sessionProto.contains("  io.github.anupambasak.gradle.testenums.proto.AppConstantsProto.TxnType txnType = 2;"));
        assertTrue(sessionProto.contains("  io.github.anupambasak.gradle.testenums.proto.AppConstantsProto.RecordStatus recordStatus = 3;"));
        assertTrue(sessionProto.contains("  repeated io.github.anupambasak.gradle.testenums.proto.AppConstantsProto.TxnType txnHistory = 4;"));

        Path constantsProtoPath = enums("AppConstantsProto.proto");
        assertTrue(Files.exists(constantsProtoPath), "AppConstantsProto.proto should be generated");
        String constantsProto = Files.readString(constantsProtoPath);
        assertTrue(constantsProto.contains("message AppConstantsProto {"));
        assertTrue(constantsProto.contains("enum TxnType {"));
        assertTrue(constantsProto.contains("  TXN_TYPE_BOOKING = 0;"));
        assertTrue(constantsProto.contains("enum RecordStatus {"));
        assertTrue(constantsProto.contains("  RECORD_STATUS_FLUSHED = 0;"));
        assertFalse(Files.exists(enums("TxnTypeProto.proto")));
    }

    @Test
    void verifyProtoFromSessionPojo() {
        SessionPojo pojo = new SessionPojo();
        pojo.setSessionNumber(42);
        pojo.setTxnType(AppConstants.TxnType.CANCELLATION);
        pojo.setRecordStatus(AppConstants.RecordStatus.BOOKED);
        pojo.setTxnHistory(List.of(AppConstants.TxnType.BOOKING, AppConstants.TxnType.CANCELLATION));

        SessionPojoProto proto = SessionPojoProto.newBuilder()
                .setSessionNumber(pojo.getSessionNumber())
                .setTxnType(AppConstantsProto.TxnType.forNumber(pojo.getTxnType().ordinal()))
                .setRecordStatus(AppConstantsProto.RecordStatus.forNumber(pojo.getRecordStatus().ordinal()))
                .addTxnHistory(AppConstantsProto.TxnType.TXN_TYPE_BOOKING)
                .addTxnHistory(AppConstantsProto.TxnType.TXN_TYPE_CANCELLATION)
                .build();

        assertEquals(42, proto.getSessionNumber());
        assertEquals(AppConstantsProto.TxnType.TXN_TYPE_CANCELLATION, proto.getTxnType());
        assertEquals(AppConstantsProto.RecordStatus.RECORD_STATUS_BOOKED, proto.getRecordStatus());
        assertEquals(2, proto.getTxnHistoryCount());
        assertNotNull(proto.toByteArray());
    }

    @Test
    void verifyExcludedPathsAreNotGenerated() {
        // excluded directory
        assertFalse(Files.exists(protoFile("dtos/internal", "InternalAuditPojoProto.proto")), "classes under an excluded directory must be skipped");
        // excluded single file
        assertFalse(Files.exists(dtos("LegacyPojoProto.proto")), "an excluded file must be skipped");
        // the rest of the same source directory is still generated
        assertTrue(Files.exists(dtos("PersonPojoProto.proto")));
    }

    @Test
    void verifyArrayPojoProtoContent() throws IOException {
        Path arrayPojoProtoPath = dtos("ArrayPojoProto.proto");
        assertTrue(Files.exists(arrayPojoProtoPath), "ArrayPojoProto.proto should be generated");

        String content = Files.readString(arrayPojoProtoPath);
        assertTrue(content.contains("import \"io/github/anupambasak/gradle/dtos/proto/AddressProto.proto\";"));
        assertFalse(content.contains("[]"), "array brackets must not leak into the proto");
        assertTrue(content.contains("  AddressProto primaryAddress = 1;"));
        assertTrue(content.contains("  repeated AddressProto otherAddresses = 2;"));
        assertTrue(content.contains("  repeated string tags = 3;"));
        assertTrue(content.contains("  repeated int32 scores = 4;"));
        assertTrue(content.contains("  bytes payload = 5;"));
    }

    @Test
    void verifyProtoFromArrayPojo() {
        Address address = new Address();
        address.setStreet("1 Array Rd");
        address.setCity("Indexville");
        address.setZipCode(10001);

        ArrayPojo pojo = new ArrayPojo();
        pojo.setOtherAddresses(new Address[]{address, address});
        pojo.setTags(new String[]{"a", "b"});
        pojo.setScores(new int[]{7, 9});
        pojo.setPayload(new byte[]{1, 2, 3});

        AddressProto addressProto = AddressProto.newBuilder()
                .setStreet(address.getStreet())
                .setCity(address.getCity())
                .setZipCode(address.getZipCode())
                .build();

        ArrayPojoProto.Builder builder = ArrayPojoProto.newBuilder();
        for (Address ignored : pojo.getOtherAddresses()) {
            builder.addOtherAddresses(addressProto);
        }
        builder.addAllTags(List.of(pojo.getTags()));
        for (int score : pojo.getScores()) {
            builder.addScores(score);
        }
        builder.setPayload(com.google.protobuf.ByteString.copyFrom(pojo.getPayload()));
        ArrayPojoProto proto = builder.build();

        assertEquals(2, proto.getOtherAddressesCount());
        assertEquals("Indexville", proto.getOtherAddresses(1).getCity());
        assertEquals(List.of("a", "b"), proto.getTagsList());
        assertEquals(List.of(7, 9), proto.getScoresList());
        assertArrayEquals(pojo.getPayload(), proto.getPayload().toByteArray());
    }

    @Test
    void verifyGenericApiResponseProtoContent() throws IOException {
        String apiResponse = Files.readString(dtos("ApiResponseProto.proto"));
        assertTrue(apiResponse.contains("import \"google/protobuf/any.proto\";"));
        assertFalse(apiResponse.contains("T.proto"), "type parameters must not be imported as messages");
        assertFalse(apiResponse.contains("serialVersionUID"), "static fields must be skipped");
        assertTrue(apiResponse.contains("message ApiResponseProto {"));
        assertTrue(apiResponse.contains("  bool success = 1;"));
        assertTrue(apiResponse.contains("  int32 errorCode = 2;"));
        assertTrue(apiResponse.contains("  string message = 3;"));
        assertTrue(apiResponse.contains("  google.protobuf.Any data = 4;"));
        assertTrue(apiResponse.contains("  repeated google.protobuf.Any items = 5;"));
        assertTrue(apiResponse.contains("  google.protobuf.Any metadata = 6;"));
        assertTrue(apiResponse.contains("  repeated google.protobuf.Any extras = 7;"));
        assertFalse(apiResponse.contains("Object.proto"), "java.lang.Object must map to Any, not a message");

        String envelope = Files.readString(dtos("ResponseEnvelopeProto.proto"));
        assertTrue(envelope.contains("import \"io/github/anupambasak/gradle/dtos/proto/ApiResponseProto.proto\";"));
        assertFalse(envelope.contains("<"), "type arguments must not leak into the proto");
        assertTrue(envelope.contains("  ApiResponseProto addressResponse = 1;"));
        assertTrue(envelope.contains("  repeated ApiResponseProto history = 2;"));
    }

    @Test
    void verifyProtoFromApiResponse() throws Exception {
        AddressProto addressProto = AddressProto.newBuilder()
                .setStreet("9 Generic Way")
                .setCity("Anytown")
                .setZipCode(12345)
                .build();

        ApiResponseProto response = ApiResponseProto.newBuilder()
                .setSuccess(true)
                .setErrorCode(0)
                .setMessage("ok")
                .setData(com.google.protobuf.Any.pack(addressProto))
                .addItems(com.google.protobuf.Any.pack(addressProto))
                .build();

        ResponseEnvelopeProto envelope = ResponseEnvelopeProto.newBuilder()
                .setAddressResponse(response)
                .addHistory(response)
                .build();

        ResponseEnvelopeProto parsed =
                ResponseEnvelopeProto.parseFrom(envelope.toByteArray());
        com.google.protobuf.Any data = parsed.getAddressResponse().getData();
        assertTrue(data.is(AddressProto.class));
        assertEquals("9 Generic Way", data.unpack(AddressProto.class).getStreet());
        assertEquals(1, parsed.getHistoryCount());
    }

    @Test
    void verifySameNamedClassesInDifferentPackagesKeepTheirNames() throws IOException {
        // catalog.PriceDetailDTO and billing.PriceDetailDto differ only in case. Each is generated in its own
        // package directory (dtos/catalog/proto, dtos/billing/proto), so they no longer collide (even on Windows/macOS) and are not renamed.
        Path catalogPath = protoFile("dtos/catalog", "PriceDetailDTOProto.proto");
        Path billingPath = protoFile("dtos/billing", "PriceDetailDtoProto.proto");
        assertTrue(Files.exists(catalogPath), "catalog/proto/PriceDetailDTOProto.proto should be generated");
        assertTrue(Files.exists(billingPath), "billing/proto/PriceDetailDtoProto.proto should be generated");
        assertFalse(Files.exists(protoFile("dtos/catalog", "CatalogPriceDetailDTOProto.proto")), "no package prefix is needed");

        String catalog = Files.readString(catalogPath);
        assertTrue(catalog.contains("package io.github.anupambasak.gradle.dtos.catalog.proto;"));
        assertTrue(catalog.contains("option java_package = \"io.github.anupambasak.gradle.dtos.catalog.proto\";"));
        assertTrue(catalog.contains("message PriceDetailDTOProto {"));
        assertTrue(catalog.contains("  string sku = 1;"));
        assertTrue(catalog.contains("  double listPrice = 2;"));

        String billing = Files.readString(billingPath);
        assertTrue(billing.contains("package io.github.anupambasak.gradle.dtos.billing.proto;"));
        assertTrue(billing.contains("message PriceDetailDtoProto {"));
        assertTrue(billing.contains("enum Kind {"));
        assertTrue(billing.contains("  KIND_CHARGE = 0;"));
        assertTrue(billing.contains("  Kind kind = 3;"));

        String summary = Files.readString(dtos("PriceSummaryPojoProto.proto"));
        assertTrue(summary.contains("import \"io/github/anupambasak/gradle/dtos/billing/proto/PriceDetailDtoProto.proto\";"));
        assertTrue(summary.contains("import \"io/github/anupambasak/gradle/dtos/catalog/proto/PriceDetailDTOProto.proto\";"));
        assertTrue(summary.contains("  io.github.anupambasak.gradle.dtos.catalog.proto.PriceDetailDTOProto catalogPrice = 1;"));
        assertTrue(summary.contains("  repeated io.github.anupambasak.gradle.dtos.billing.proto.PriceDetailDtoProto billedPrices = 2;"));
        assertTrue(summary.contains("  io.github.anupambasak.gradle.dtos.billing.proto.PriceDetailDtoProto.Kind lastKind = 3;"));
    }

    @Test
    void verifyProtoFromSameNamedClasses() {
        PriceDetailDTOProto catalogPrice = PriceDetailDTOProto.newBuilder()
                .setSku("SKU-1")
                .setListPrice(9.5)
                .build();
        PriceDetailDtoProto billed = PriceDetailDtoProto.newBuilder()
                .setInvoiceId("INV-1")
                .setAmount(9.5)
                .setKind(PriceDetailDtoProto.Kind.KIND_CHARGE)
                .build();

        PriceSummaryPojoProto summary = PriceSummaryPojoProto.newBuilder()
                .setCatalogPrice(catalogPrice)
                .addBilledPrices(billed)
                .setLastKind(PriceDetailDtoProto.Kind.KIND_REFUND)
                .build();

        assertEquals("SKU-1", summary.getCatalogPrice().getSku());
        assertEquals("INV-1", summary.getBilledPrices(0).getInvoiceId());
        assertEquals(PriceDetailDtoProto.Kind.KIND_REFUND, summary.getLastKind());
    }

    @Test
    void verifyCollectionPojoProtoContent() throws IOException {
        String content = Files.readString(dtos("CollectionPojoProto.proto"));
        assertTrue(content.contains("import \"io/github/anupambasak/gradle/dtos/proto/AddressProto.proto\";"));
        assertTrue(content.contains("import \"io/github/anupambasak/gradle/testenums/proto/TestEnumProto.proto\";"));
        assertFalse(content.contains("Set.proto"), "Set must not be treated as a message");
        assertFalse(content.contains("<"), "type arguments must not leak into the proto");
        assertTrue(content.contains("  repeated string tags = 1;"));
        assertTrue(content.contains("  repeated AddressProto addresses = 2;"));
        assertTrue(content.contains("  repeated int32 codes = 3;"));
        assertTrue(content.contains("  repeated int64 ids = 4;"));
        assertTrue(content.contains("  repeated io.github.anupambasak.gradle.testenums.proto.TestEnumProto flags = 5;"));
        assertTrue(content.contains("  repeated AddressProto history = 6;"));
        assertTrue(content.contains("  repeated string queue = 7;"));
    }

    @Test
    void verifyProtoFromCollectionPojo() {
        java.util.Set<String> tags = new java.util.LinkedHashSet<>(List.of("red", "blue"));

        CollectionPojoProto proto = CollectionPojoProto.newBuilder()
                .addAllTags(tags)
                .addAllCodes(java.util.Set.of(7))
                .addFlags(TestEnumProto.TEST_ENUM_VALUE1)
                .build();

        assertEquals(tags, new java.util.LinkedHashSet<>(proto.getTagsList()));
        assertEquals(List.of(7), proto.getCodesList());
        assertEquals(1, proto.getFlagsCount());
    }

    @Test
    void verifyMapOfListUsesWrapperMessages() throws IOException {
        String grouped = Files.readString(dtos("GroupedPojoProto.proto"));
        assertTrue(grouped.contains("import \"io/github/anupambasak/gradle/dtos/proto/AddressListProto.proto\";"));
        assertTrue(grouped.contains("import \"io/github/anupambasak/gradle/dtos/proto/StringListProto.proto\";"));
        assertFalse(grouped.contains("repeated AddressProto>"), "map values must not be repeated");
        assertTrue(grouped.contains("  map<string, AddressListProto> addressesByCity = 1;"));
        assertTrue(grouped.contains("  map<string, StringListProto> tagsByKey = 2;"));
        assertTrue(grouped.contains("  repeated AddressListProto addressPages = 3;"));

        String addressList = Files.readString(dtos("AddressListProto.proto"));
        assertTrue(addressList.contains("import \"io/github/anupambasak/gradle/dtos/proto/AddressProto.proto\";"));
        assertTrue(addressList.contains("message AddressListProto {\n  repeated AddressProto items = 1;\n}"));

        String stringList = Files.readString(dtos("StringListProto.proto"));
        assertTrue(stringList.contains("message StringListProto {\n  repeated string items = 1;\n}"));
        assertFalse(stringList.contains("import "));
    }

    @Test
    void verifyProtoFromGroupedPojo() throws Exception {
        AddressProto pune = AddressProto.newBuilder()
                .setStreet("1 FC Road").setCity("Pune").setZipCode(411004).build();
        AddressListProto puneAddresses = AddressListProto.newBuilder()
                .addItems(pune).addItems(pune).build();

        GroupedPojoProto grouped = GroupedPojoProto.newBuilder()
                .putAddressesByCity("Pune", puneAddresses)
                .putTagsByKey("colors", StringListProto.newBuilder().addItems("red").addItems("blue").build())
                .addAddressPages(puneAddresses)
                .build();

        GroupedPojoProto parsed =
                GroupedPojoProto.parseFrom(grouped.toByteArray());
        assertEquals(2, parsed.getAddressesByCityOrThrow("Pune").getItemsCount());
        assertEquals(List.of("red", "blue"), parsed.getTagsByKeyOrThrow("colors").getItemsList());
        assertEquals("Pune", parsed.getAddressPages(0).getItems(0).getCity());
    }

    @Test
    void verifyProtoFromMapPojo() {
        // Create Address POJO for map value
        Address addressPojo = new Address();
        addressPojo.setStreet("456 Oak Ave");
        addressPojo.setCity("Othertown");
        addressPojo.setZipCode(54321);

        // Create MapPojo
        MapPojo mapPojo = new MapPojo();
        mapPojo.setSimpleMap(Collections.singletonMap("one", 1));
        mapPojo.setComplexMap(Collections.singletonMap("home", addressPojo));

        // Create Proto from MapPojo
        AddressProto addressProto = AddressProto.newBuilder()
                .setStreet(addressPojo.getStreet())
                .setCity(addressPojo.getCity())
                .setZipCode(addressPojo.getZipCode())
                .build();

        MapPojoProto mapProto = MapPojoProto.newBuilder()
                .putSimpleMap("one", 1)
                .putComplexMap("home", addressProto)
                .build();

        // Assert values
        assertEquals(mapPojo.getSimpleMap().get("one"), mapProto.getSimpleMapMap().get("one"));
        assertEquals(mapPojo.getComplexMap().get("home").getStreet(), mapProto.getComplexMapMap().get("home").getStreet());
    }

}

