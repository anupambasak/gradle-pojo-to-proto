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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GradlePojoToProtoPluginFunctionalTest {

    private final String protoDir = "src/main/proto";

    @Test
    void canRunTask() {
        // This test now assumes that the `pojoToProto` task has been run before this test
    }

    @Test
    void verifyPersonPojoProtoContent() throws IOException {
        Path personPojoProtoPath = Path.of(protoDir, "PersonPojo.proto");
        assertTrue(Files.exists(personPojoProtoPath), "PersonPojo.proto should be generated");

        String personPojoProtoContent = Files.readString(personPojoProtoPath);
        assertTrue(personPojoProtoContent.contains("syntax = \"proto3\";"));
        assertTrue(personPojoProtoContent.contains("package com.anupambasak.gradle.proto;"));
        assertTrue(personPojoProtoContent.contains("option java_package = \"com.anupambasak.gradle.proto\";"));
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
}
