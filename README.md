# Gradle POJO to Proto Plugin

A Gradle plugin to generate Protobuf (.proto) files from Java POJO classes.

## Features

*   **Core Conversion:** Converts Java POJOs into Protobuf messages.
*   **Type Mapping:** Maps Java primitive types (int, long, String, etc.), lists, and `java.time.Instant` to their corresponding Protobuf types.
*   **Nested Objects:** Handles nested POJOs by generating separate `.proto` files and adding the necessary import statements.
*   **File Generation Modes:**
    *   **Multi-file:** Generates one `.proto` file for each POJO (default).
    *   **Single-file:** Aggregates all generated messages into a single `.proto` file.
*   **Customizable Package Naming:** Allows for a custom Protobuf package name to be set in the configuration. Defaults to the Gradle project's group if not provided.

## Usage

### Applying the Plugin

To use the plugin, apply it in your `build.gradle` file:

```gradle
plugins {
    id 'pojo-to-proto'
}
```

### Configuration

The plugin can be configured using the `pojoToProto` extension block in your `build.gradle`:

```gradle
pojoToProto {
    source = project.layout.projectDirectory.dir("src/main/java/com/example/pojo")
    destination = project.layout.buildDirectory.dir("generated/proto")
    singleFile = false // optional, defaults to false
    packageName = "com.example.proto" // optional, defaults to project group
}
```

*   `source`: The directory containing the Java POJO source files.
*   `destination`: The directory where the generated `.proto` files will be saved.
*   `singleFile`: If `true`, all messages will be generated in a single `.proto` file named after the project. If `false` (the default), one `.proto` file will be generated for each POJO.
*   `packageName`: The package name to be used in the generated `.proto` files.

### Task

The plugin creates a task named `pojoToProto`. You can run it directly:

```bash
./gradlew pojoToProto
```

## Example

Given the following POJO in `src/main/java/com/example/pojo/User.java`:

```java
package com.example.pojo;

public class User {
    private String name;
    private int age;
}
```

The `pojoToProto` task will generate the following file in `build/generated/proto/User.proto`:

```protobuf
syntax = "proto3";

package com.example.proto;

option java_package = "com.example.proto";

message User {
  string name = 1;
  int32 age = 2;
}
```

## Building from Source

To build the plugin from source:

1.  Clone the repository.
2.  Run the following command to build the plugin and publish it to the local build repository:

    ```bash
    ./gradlew :plugin:publish
    ```
