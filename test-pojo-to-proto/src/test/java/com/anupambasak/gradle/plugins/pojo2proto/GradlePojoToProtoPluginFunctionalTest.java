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

import com.anupambasak.gradle.dtos.Address;
import com.anupambasak.gradle.dtos.PersonPojo;
import com.anupambasak.gradle.dtos.TimePojo;
import com.anupambasak.gradle.testenums.Conts;
import com.anupambasak.gradle.testenums.EnumPojo;
import com.anupambasak.gradle.testenums.TestEnum;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class GradlePojoToProtoPluginFunctionalTest {

    private final String protoDir = "src/main/proto";


    @Test
    void verifyPersonPojoProtoContent() throws IOException {
        Path personPojoProtoPath = Path.of(protoDir, "PersonPojo.proto");
        assertTrue(Files.exists(personPojoProtoPath), "PersonPojo.proto should be generated");

        String personPojoProtoContent = Files.readString(personPojoProtoPath);
        assertTrue(personPojoProtoContent.contains("syntax = \"proto3\";"));
        assertTrue(personPojoProtoContent.contains("package com.anupambasak.gradle.proto;"));
        assertTrue(personPojoProtoContent.contains("option java_package = \"com.anupambasak.gradle.proto\";"));
        assertTrue(personPojoProtoContent.contains("option java_multiple_files = true;"));
        assertTrue(personPojoProtoContent.contains("import \"Address.proto\";"));
        assertTrue(personPojoProtoContent.contains("import \"google/protobuf/timestamp.proto\";"));
        assertTrue(personPojoProtoContent.contains("message PersonPojo {"));
        assertTrue(personPojoProtoContent.contains("  string name = 1;"));
        assertTrue(personPojoProtoContent.contains("  int32 age = 2;"));
        assertTrue(personPojoProtoContent.contains("  Address address = 3;"));
        assertTrue(personPojoProtoContent.contains("  repeated Address previousAddresses = 4;"));
        assertTrue(personPojoProtoContent.contains("  google.protobuf.Timestamp createdAt = 5;"));
    }

    @Test
    void verifyTimePojoProtoContent() throws IOException {
        Path timePojoProtoPath = Path.of(protoDir, "TimePojo.proto");
        assertTrue(Files.exists(timePojoProtoPath), "TimePojo.proto should be generated");

        String timePojoProtoContent = Files.readString(timePojoProtoPath);
        assertTrue(timePojoProtoContent.contains("syntax = \"proto3\";"));
        assertTrue(timePojoProtoContent.contains("package com.anupambasak.gradle.proto;"));
        assertTrue(timePojoProtoContent.contains("option java_package = \"com.anupambasak.gradle.proto\";"));
        assertTrue(timePojoProtoContent.contains("option java_multiple_files = true;"));
        assertTrue(timePojoProtoContent.contains("import \"google/protobuf/duration.proto\";"));
        assertTrue(timePojoProtoContent.contains("import \"google/protobuf/timestamp.proto\";"));
        assertTrue(timePojoProtoContent.contains("import \"google/type/date.proto\";"));
        assertTrue(timePojoProtoContent.contains("import \"google/type/timeofday.proto\";"));
        assertTrue(timePojoProtoContent.contains("message TimePojo {"));
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
    }

        @Test
        void verifyMapPojoProtoContent() throws IOException {
            Path mapPojoProtoPath = Path.of(protoDir, "MapPojo.proto");
            assertTrue(Files.exists(mapPojoProtoPath), "MapPojo.proto should be generated");
            String mapPojoProtoContent = Files.readString(mapPojoProtoPath);
            assertTrue(mapPojoProtoContent.contains("syntax = \"proto3\";"));
            assertTrue(mapPojoProtoContent.contains("package com.anupambasak.gradle.proto;"));
            assertTrue(mapPojoProtoContent.contains("option java_package = \"com.anupambasak.gradle.proto\";"));
            assertTrue(mapPojoProtoContent.contains("option java_multiple_files = true;"));
            assertTrue(mapPojoProtoContent.contains("import \"Address.proto\";"));
            assertTrue(mapPojoProtoContent.contains("message MapPojo {"));
            assertTrue(mapPojoProtoContent.contains("  map<string, int32> simpleMap = 1;"));
            assertTrue(mapPojoProtoContent.contains("  map<string, Address> complexMap = 2;"));

        }

    

        @Test

        void verifyContsProtoContent() throws IOException {
            Path contsProtoPath = Path.of(protoDir, "Conts.proto");
            assertTrue(Files.exists(contsProtoPath), "Conts.proto should be generated");
            String contsProtoContent = Files.readString(contsProtoPath);
            assertTrue(contsProtoContent.contains("syntax = \"proto3\";"));
            assertTrue(contsProtoContent.contains("package com.anupambasak.gradle.proto;"));
            assertTrue(contsProtoContent.contains("option java_package = \"com.anupambasak.gradle.proto\";"));
            assertTrue(contsProtoContent.contains("option java_multiple_files = true;"));
            assertFalse(contsProtoContent.contains("import \"BerthType.proto\";")); // No incorrect import
            assertTrue(contsProtoContent.contains("message Conts {"));
            assertTrue(contsProtoContent.contains("enum b {"));
            assertTrue(contsProtoContent.contains("  c = 0;"));
            assertTrue(contsProtoContent.contains("  d = 1;"));
            assertTrue(contsProtoContent.contains("  e = 2;"));
            assertTrue(contsProtoContent.contains("  f = 3;"));
            assertTrue(contsProtoContent.contains("  g = 4;"));
            assertTrue(contsProtoContent.contains("  h = 5;"));
            assertTrue(contsProtoContent.contains("  i = 6;"));
            assertTrue(contsProtoContent.contains("  j = 7;"));
            assertTrue(contsProtoContent.contains("  k = 8;"));
            assertTrue(contsProtoContent.contains("  l = 9;"));
            assertTrue(contsProtoContent.contains("  m = 10;"));
            assertTrue(contsProtoContent.contains("  n = 11;"));
            assertTrue(contsProtoContent.contains("  o = 12;"));
        }


    @Test
    void verifyEnumPojosProtoContent() throws IOException {
        Path enumPojoProtoPath = Path.of(protoDir, "EnumPojo.proto");
        assertTrue(Files.exists(enumPojoProtoPath), "EnumPojo.proto should be generated");

        String enumPojoProtoContent = Files.readString(enumPojoProtoPath);
        assertTrue(enumPojoProtoContent.contains("syntax = \"proto3\";"));
        assertTrue(enumPojoProtoContent.contains("package com.anupambasak.gradle.proto;"));
        assertTrue(enumPojoProtoContent.contains("option java_package = \"com.anupambasak.gradle.proto\";"));
        assertTrue(enumPojoProtoContent.contains("option java_multiple_files = true;"));
        assertTrue(enumPojoProtoContent.contains("import \"Conts.proto\";"));
        assertTrue(enumPojoProtoContent.contains("import \"TestEnum.proto\";"));
        assertTrue(enumPojoProtoContent.contains("message EnumPojo {"));
        assertTrue(enumPojoProtoContent.contains("  TestEnum testEnum = 1;"));
        assertTrue(enumPojoProtoContent.contains("  Conts.b berthType = 2;"));

        Path testEnumProtoPath = Path.of(protoDir, "TestEnum.proto");
        assertTrue(Files.exists(testEnumProtoPath), "TestEnum.proto should be generated");

        String testEnumProtoContent = Files.readString(testEnumProtoPath);
        assertTrue(testEnumProtoContent.contains("syntax = \"proto3\";"));
        assertTrue(testEnumProtoContent.contains("package com.anupambasak.gradle.proto;"));
        assertTrue(testEnumProtoContent.contains("option java_package = \"com.anupambasak.gradle.proto\";"));
        assertTrue(testEnumProtoContent.contains("option java_multiple_files = true;"));
        assertTrue(testEnumProtoContent.contains("enum TestEnum {"));
        assertTrue(testEnumProtoContent.contains("  VALUE1 = 0;"));
        assertTrue(testEnumProtoContent.contains("  VALUE2 = 1;"));
        assertTrue(testEnumProtoContent.contains("  VALUE3 = 2;"));
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
        personPojo.setAddress(addressPojo);
        personPojo.setPreviousAddresses(Collections.singletonList(addressPojo));
        personPojo.setCreatedAt(Instant.now());
        personPojo.setDob(LocalDateTime.of(1990, 1, 1, 0, 0));

        // Create Proto from PersonPojo
        com.anupambasak.gradle.proto.Address addressProto = com.anupambasak.gradle.proto.Address.newBuilder()
                .setStreet(addressPojo.getStreet())
                .setCity(addressPojo.getCity())
                .setZipCode(addressPojo.getZipCode())
                .build();

        com.anupambasak.gradle.proto.PersonPojo personProto = com.anupambasak.gradle.proto.PersonPojo.newBuilder()
                .setName(personPojo.getName())
                .setAge(personPojo.getAge())
                .setAddress(addressProto)
                .addPreviousAddresses(addressProto)
                .setCreatedAt(Timestamps.fromMillis(personPojo.getCreatedAt().toEpochMilli()))
                .setDob(Timestamp.newBuilder().setSeconds(personPojo.getDob().toEpochSecond(ZoneOffset.UTC)).build())
                .build();

        // Assert values
        assertEquals(personPojo.getName(), personProto.getName());
        assertEquals(personPojo.getAge(), personProto.getAge());
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

        // Create Proto from TimePojo
        com.anupambasak.gradle.proto.TimePojo timeProto = com.anupambasak.gradle.proto.TimePojo.newBuilder()
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
        com.anupambasak.gradle.proto.EnumPojo enumProto = com.anupambasak.gradle.proto.EnumPojo.newBuilder()
                .setTestEnum(com.anupambasak.gradle.proto.TestEnum.VALUE2)
                .setBerthType(com.anupambasak.gradle.proto.Conts.b.c)
                .build();

        // Assert values
        assertEquals(enumPojo.getTestEnum().name(), enumProto.getTestEnum().name());
        assertEquals(enumPojo.getBerthType().name(), enumProto.getBerthType().name());
    }

    @Test
    void verifyProtoFromMapPojo() {
        // Create Address POJO for map value
        Address addressPojo = new Address();
        addressPojo.setStreet("456 Oak Ave");
        addressPojo.setCity("Othertown");
        addressPojo.setZipCode(54321);

        // Create MapPojo
        com.anupambasak.gradle.dtos.MapPojo mapPojo = new com.anupambasak.gradle.dtos.MapPojo();
        mapPojo.setSimpleMap(Collections.singletonMap("one", 1));
        mapPojo.setComplexMap(Collections.singletonMap("home", addressPojo));

        // Create Proto from MapPojo
        com.anupambasak.gradle.proto.Address addressProto = com.anupambasak.gradle.proto.Address.newBuilder()
                .setStreet(addressPojo.getStreet())
                .setCity(addressPojo.getCity())
                .setZipCode(addressPojo.getZipCode())
                .build();

        com.anupambasak.gradle.proto.MapPojo mapProto = com.anupambasak.gradle.proto.MapPojo.newBuilder()
                .putSimpleMap("one", 1)
                .putComplexMap("home", addressProto)
                .build();

        // Assert values
        assertEquals(mapPojo.getSimpleMap().get("one"), mapProto.getSimpleMapMap().get("one"));
        assertEquals(mapPojo.getComplexMap().get("home").getStreet(), mapProto.getComplexMapMap().get("home").getStreet());
    }

}

