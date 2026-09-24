# POJO to Proto Gradle Plugin

A Gradle plugin to generate Protobuf (.proto) files from Java POJO classes.

## Features

*   **Core Conversion:** Converts Java POJOs into Protobuf messages.
*   **Type Mapping:** Maps Java primitive types (int, long, String, etc.), lists, `java.util.Map`, and `java.time` types to their corresponding Protobuf types.
    *   `List`, `ArrayList`, `LinkedList`, `Collection`, `Set`, `HashSet`, `LinkedHashSet`, `TreeSet`, `SortedSet`, `NavigableSet` -> `repeated` (element uniqueness of a `Set` is not enforced by Protobuf)
    *   Collections where Protobuf cannot use `repeated` (a map value, or a list inside a list) get a generated wrapper message holding `repeated <Type> items = 1`. For example `Map<String, List<MyPojo>> groups` becomes `map<string, MyPojoList> groups` with `message MyPojoList { repeated MyPojo items = 1; }` in `MyPojoList.proto` (shared by every message that needs it); `List<List<MyPojo>>` becomes `repeated MyPojoList`. Wrappers for scalars and well-known types are named after them (`StringList`, `TimestampList`). If the element is declared in the same file as the message using it (e.g. a `Map<String, List<Self>>`), the wrapper is nested inside that message to avoid a circular import.
    *   `java.time.Instant`, `java.time.ZonedDateTime`, `java.time.LocalDateTime`, `java.util.Date` -> `google.protobuf.Timestamp`
    *   `java.time.LocalDate` -> `google.type.Date`
    *   `java.time.LocalTime` -> `google.type.TimeOfDay`
    *   `java.time.Duration` -> `google.protobuf.Duration`
    *   `java.time.Period` -> `string`
    *   `java.util.UUID` -> `string`
    *   `java.lang.Object`, class type parameters and wildcards (the `T` in `ApiResponse<T>`, `List<T>`, `List<?>`) -> `google.protobuf.Any` (imports `google/protobuf/any.proto`). Pack the concrete message with `Any.pack(...)`.
    *   Parameterized fields such as `ApiResponse<User>` -> `ApiResponse` (Protobuf messages cannot take type arguments).
    *   `short`/`Short`, `byte`/`Byte` -> `int32`
    *   `char`/`Character` -> `string`
    *   `byte[]` -> `bytes`
    *   Arrays (`Type[] name` or `Type name[]`) -> `repeated Type`. Multi-dimensional arrays (other than `byte[][]` -> `repeated bytes`) are not supported by Protobuf and fail with a clear error.
*   **Nested Objects:** Handles nested POJOs by generating separate `.proto` files and adding the necessary import statements.
*   **Enums:** Supports simple and nested enums. Nested enums are generated within their parent message. Enums nested in another class or interface (e.g. an `AppConstants` interface holding shared enums) are referenced as `AppConstants.TxnType` with `import "AppConstants.proto";`, whether the Java field uses the simple imported name (`TxnType`), the qualified name, a wildcard or a static import. This works across packages as long as the file declaring the enum is included in `source`. If it is not, the generator cannot tell the type is an enum; a qualified reference such as `AccountingConstants.SiteId` is then assumed to be defined in `AccountingConstants.proto`, which you must provide. Enum value names can optionally be prefixed with the enum name (`prefixEnumNames`), following the Protobuf style guide.
*   **File Generation Modes:**
    *   **Multi-file:** Generates one `.proto` file for each POJO and top-level enum (default).
    *   **Single-file:** Aggregates all generated messages and enums into a single `.proto` file.
*   **Multiple Source Directories:** Supports specifying multiple source directories for POJOs using `from(...)`.
*   **Name clashes:** All messages share one Protobuf package, and on case-insensitive file systems (Windows, macOS) `FareDetailDTO.proto` and `FareDetailDto.proto` are the same file. When two classes' names are equal or differ only in case, both are renamed by prefixing the last segment of their Java package in PascalCase (`com.x.pricing.FareDetailDTO` -> `PricingFareDetailDTO`, `com.x.booking.FareDetailDto` -> `BookingFareDetailDto`), using more segments if needed. Fields, imports, nested enum references and file names follow the new names, and each rename is logged as a warning. Classes without a clash keep their names.
*   **Exclusions:** Skip specific directories (including their subdirectories) or individual files inside the source directories using `exclude.from(...)`.
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
    exclude.from(project.layout.projectDirectory.dir("src/main/java/com/example/pojo/internal")) // optional
    exclude.from(project.layout.projectDirectory.file("src/main/java/com/example/pojo/Legacy.java")) // optional
    destination = layout.buildDirectory.dir("generated/proto")
    singleFile = false // optional, defaults to false
    prefixEnumNames = false // optional, defaults to false
    packageName = "com.example.proto" // optional, defaults to project group
}
```

*   `source`: A `ConfigurableFileCollection` of directories containing the Java POJO source files. Use `source.from(...)` to add directories.
*   `exclude`: An optional `ConfigurableFileCollection` of directories or files to leave out. Use `exclude.from(...)` to add as many paths as needed. A `.java` file is skipped if it is, or is anywhere inside, an excluded path. Matching is by whole path segments, so excluding `pojo/internal` does not exclude `pojo/internal2`.
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
