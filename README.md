# POJO to Proto Gradle Plugin

A Gradle plugin to generate Protobuf (.proto) files from Java POJO classes.

## Features

*   **Core Conversion:** Converts Java POJOs into Protobuf messages.
*   **Type Mapping:** Maps Java primitive types (int, long, String, etc.), lists, `java.util.Map`, and `java.time` types to their corresponding Protobuf types.
    *   `java.time.Instant`, `java.time.ZonedDateTime`, `java.time.LocalDateTime` -> `google.protobuf.Timestamp`
    *   `java.time.LocalDate` -> `google.type.Date`
    *   `java.time.LocalTime` -> `google.type.TimeOfDay`
    *   `java.time.Duration` -> `google.protobuf.Duration`
    *   `java.time.Period` -> `string`
    *   `java.util.UUID` -> `string`
    *   `short`/`Short`, `byte`/`Byte` -> `int32`
    *   `char`/`Character` -> `string`
    *   `byte[]` -> `bytes`
*   **Nested Objects:** Handles nested POJOs by generating separate `.proto` files and adding the necessary import statements.
*   **Enums:** Supports simple and nested enums. Nested enums are generated within their parent message. Enums nested in another class or interface (e.g. a `PnrConstants` interface holding shared enums) are referenced as `PnrConstants.TxnType` with `import "PnrConstants.proto";`, whether the Java field uses the simple imported name (`TxnType`), the qualified name, a wildcard or a static import. Enum value names can optionally be prefixed with the enum name (`prefixEnumNames`), following the Protobuf style guide.
*   **File Generation Modes:**
    *   **Multi-file:** Generates one `.proto` file for each POJO and top-level enum (default).
    *   **Single-file:** Aggregates all generated messages and enums into a single `.proto` file.
*   **Multiple Source Directories:** Supports specifying multiple source directories for POJOs using `from(...)`.
*   **Customizable Package Naming:** Allows for a custom Protobuf package name to be set in the configuration. Defaults to the Gradle project's group if not provided.

## Usage

### Applying the Plugin

To use the plugin, apply it in your `build.gradle` file:

```gradle
plugins {
    id 'io.github.anupambasak.gradle-pojo-to-proto'
}
```

### Configuration

The plugin can be configured using the `pojoToProto` extension block in your `build.gradle`:

```gradle
pojoToProto {
    source.from(project.layout.projectDirectory.dir("src/main/java/com/example/pojo"))
    source.from(project.layout.projectDirectory.dir("src/main/java/com/example/another_pojo"))
    destination = layout.buildDirectory.dir("generated/proto")
    singleFile = false // optional, defaults to false
    prefixEnumNames = false // optional, defaults to false
    packageName = "com.example.proto" // optional, defaults to project group
}
```

*   `source`: A `ConfigurableFileCollection` of directories containing the Java POJO source files. Use `source.from(...)` to add directories.
*   `destination`: The directory where the generated `.proto` files will be saved.
*   `singleFile`: If `true`, all messages will be generated in a single `.proto` file named after the project. If `false` (the default), one `.proto` file will be generated for each POJO.
*   `prefixEnumNames`: If `true`, each enum value name is prefixed with the enum's name in `UPPER_SNAKE_CASE` (e.g. `OrderStatus.ACTIVE` -> `ORDER_STATUS_ACTIVE`). This follows the [Protobuf style guide](https://protobuf.dev/programming-guides/style/#enums) and avoids name clashes, since Protobuf enum values share the scope of their enclosing package or message. Values that already start with the prefix are left unchanged. Defaults to `false`.
    > **Note:** Enabling this renames the generated enum values. The binary wire format is unaffected (numbers stay the same), but JSON/text-format output and any code referencing the generated enum constants will change.
*   `packageName`: The package name to be used in the generated `.proto` files.

### Task

The plugin creates a task named `pojoToProto`. You can run it directly:

```bash
./gradlew pojoToProto --console=plain
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
option java_multiple_files = true;

message User {
  string name = 1;
  int32 age = 2;
}
```

### Enum Prefixing

Given the following enum:

```java
public enum OrderStatus {
    ACTIVE,
    CANCELLED
}
```

With `prefixEnumNames = false` (the default):

```protobuf
enum OrderStatus {
  ACTIVE = 0;
  CANCELLED = 1;
}
```

With `prefixEnumNames = true`:

```protobuf
enum OrderStatus {
  ORDER_STATUS_ACTIVE = 0;
  ORDER_STATUS_CANCELLED = 1;
}
```

## Building from Source

To build the plugin from source:

1.  Clone the repository.
2.  Run the following command to build the plugin and publish it to the local build repository:

    ```bash
    ./gradlew :plugin:publish -PpublishingPlugin=true --console=plain
    ```
