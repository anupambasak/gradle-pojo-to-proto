# POJO to Proto Gradle Plugin

A Gradle plugin to generate Protobuf (.proto) files from Java POJO classes.

## Features

*   **Core Conversion:** Converts Java POJOs into Protobuf messages.
*   **Type Mapping:** Maps Java primitive types (int, long, String, etc.), lists, `java.util.Map`, and `java.time` types to their corresponding Protobuf types.
    *   `List`, `ArrayList`, `LinkedList`, `Collection`, `Set`, `HashSet`, `LinkedHashSet`, `TreeSet`, `SortedSet`, `NavigableSet` -> `repeated` (element uniqueness of a `Set` is not enforced by Protobuf)
    *   Collections where Protobuf cannot use `repeated` (a map value, or a list inside a list) get a generated wrapper message holding `repeated <Type> items = 1`. For example `Map<String, List<MyPojo>> groups` becomes `map<string, MyPojoListProto> groups` with `message MyPojoListProto { repeated MyPojoProto items = 1; }` in `MyPojoListProto.proto` (shared by every message in that package that needs it); `List<List<MyPojo>>` becomes `repeated MyPojoListProto`. Wrappers for scalars and well-known types are named after them (`StringListProto`, `TimestampListProto`). If the element is declared in the same file as the message using it (e.g. a `Map<String, List<Self>>`), the wrapper is nested inside that message to avoid a circular import.
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
*   **Enums:** Supports simple and nested enums. Nested enums are generated within their parent message. Enums nested in another class or interface (e.g. an `AppConstants` interface holding shared enums) are referenced as `AppConstantsProto.TxnType` with `import "com/example/proto/AppConstantsProto.proto";` (fully qualified, `com.example.proto.AppConstantsProto.TxnType`, when used from another package), whether the Java field uses the simple imported name (`TxnType`), the qualified name, a wildcard or a static import. This works across packages as long as the file declaring the enum is included in `source`. If it is not, the generator cannot tell the type is an enum; a qualified reference such as `AccountingConstants.SiteId` is then assumed to be defined in `AccountingConstants.proto` at the root of `destination`, unsuffixed, which you must provide. Enum value names can optionally be prefixed with the enum name (`prefixEnumNames`), following the Protobuf style guide.
*   **File Generation Modes:**
    *   **Multi-file:** Generates one `.proto` file for each POJO and top-level enum (default).
    *   **Single-file:** Aggregates all generated messages and enums into a single `.proto` file.
*   **Multiple Source Directories:** Supports specifying multiple source directories for POJOs using `from(...)`.
*   **Package layout:** By default each `.proto` follows the package of its POJO plus `javaPackageSuffix`, so its directory, `package` and `java_package` all match: `com.example.dtos.Address` becomes `<destination>/com/example/dtos/proto/AddressProto.proto` with `package com.example.dtos.proto;` and `option java_package = "com.example.dtos.proto";`. Imports use these paths (`import "com/example/enums/proto/OrderStatusProto.proto";`) and types from other packages are referenced by their fully qualified name (`com.example.enums.proto.OrderStatusProto`). Can be switched off with `usePojoPackages = false`.
*   **Generated names that don't collide with your POJOs:** By default every generated top-level message and enum gets the suffix `Proto` (`Address` -> `message AddressProto` in `AddressProto.proto`) and the generated Java classes go into a `.proto` sub-package (`com.example.dtos.proto.AddressProto`). Both are configurable (`nameSuffix`, `javaPackageSuffix`).
*   **Name clashes:** Two classes whose names are equal or differ only in case would map to the same file on case-insensitive file systems (Windows, macOS). This can only happen within one Protobuf package: in the same Java package, or anywhere when `usePojoPackages = false` puts everything in one package. Both classes are then renamed by prefixing the last segment of their Java package in PascalCase (`com.x.pricing.FareDetailDTO` -> `PricingFareDetailDTOProto`, `com.x.booking.FareDetailDto` -> `BookingFareDetailDtoProto`), using more segments if needed, or a number when they share a package. Fields, imports, nested enum references and file names follow the new names, and each rename is logged as a warning. Classes without a clash keep their names.
*   **Exclusions:** Skip specific directories (including their subdirectories) or individual files inside the source directories using `exclude.from(...)`.

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
    destination = project.layout.projectDirectory.dir("src/main/proto")
    singleFile = false // optional, defaults to false
    prefixEnumNames = false // optional, defaults to false
    usePojoPackages = true // optional, defaults to true (false when singleFile = true)
    // packageName = "com.example.proto" // optional, flat layout / singleFile only, defaults to project group
    nameSuffix = "Proto" // optional, defaults to "Proto"
    javaPackageSuffix = ".proto" // optional, defaults to ".proto"
}
```

To get the flat layout of plugin versions before 0.2.0 (all files in one directory and one package, with unchanged names):

```gradle
pojoToProto {
    source.from(project.layout.projectDirectory.dir("src/main/java/com/example"))
    destination = project.layout.projectDirectory.dir("src/main/proto")
    usePojoPackages = false
    packageName = "com.example.proto" // optional, defaults to the project's group
    nameSuffix = ""
    javaPackageSuffix = ""
}
```

*   `source`: A `ConfigurableFileCollection` of directories containing the Java POJO source files. Use `source.from(...)` to add directories.
*   `exclude`: An optional `ConfigurableFileCollection` of directories or files to leave out. Use `exclude.from(...)` to add as many paths as needed. A `.java` file is skipped if it is, or is anywhere inside, an excluded path. Matching is by whole path segments, so excluding `pojo/internal` does not exclude `pojo/internal2`.
*   `destination`: The directory where the generated `.proto` files will be saved. With `usePojoPackages`, imports are relative to it, so it must be a proto source root (e.g. `src/main/proto`, which the `com.google.protobuf` plugin picks up).
*   `singleFile`: If `true`, all messages will be generated in a single `.proto` file named after the project, in the package given by `packageName`. If `false` (the default), one `.proto` file will be generated for each POJO.
*   `prefixEnumNames`: If `true`, each enum value name is prefixed with the enum's name in `UPPER_SNAKE_CASE` (e.g. `OrderStatus.ACTIVE` -> `ORDER_STATUS_ACTIVE`). This follows the [Protobuf style guide](https://protobuf.dev/programming-guides/style/#enums) and avoids name clashes, since Protobuf enum values share the scope of their enclosing package or message. Values that already start with the prefix are left unchanged. Defaults to `false`.
    > **Note:** Enabling this renames the generated enum values. The binary wire format is unaffected (numbers stay the same), but JSON/text-format output and any code referencing the generated enum constants will change.
*   `usePojoPackages`: If `true` (the default), every `.proto` file uses its POJO's Java package followed by `javaPackageSuffix` as its Protobuf `package` and `java_package`, and is written to the matching sub-directory of `destination` (`com.example.dtos.Address` -> `com/example/dtos/proto/AddressProto.proto`). References to types in another package are fully qualified. Collection wrapper messages (`AddressListProto`) are generated in the package of the message that uses them. Classes in the default (unnamed) package get no package. If `false`, all files are written flat into `destination` and share `packageName` as their package. Defaults to `false` when `singleFile = true`, since one file can only declare one package; setting both to `true` fails the build.
*   `packageName`: The Protobuf package shared by all generated files with the flat layout, i.e. when `usePojoPackages = false` or `singleFile = true` (`java_package` is this plus `javaPackageSuffix`). Ignored, with a warning, when `usePojoPackages` is enabled, since each file then takes its POJO's package. Defaults to the Gradle project's `group`.
*   `nameSuffix`: Appended to the name of every generated top-level message, top-level enum and wrapper message, and therefore to its file name: `Address` becomes `message AddressProto` in `AddressProto.proto`, and `List<List<Address>>` uses `AddressListProto`. Enums nested inside a message keep their name. With `prefixEnumNames`, value prefixes are based on the name without the suffix (`ORDER_STATUS_ACTIVE`, not `ORDER_STATUS_PROTO_ACTIVE`). Letters, digits and `_` only. Defaults to `"Proto"`; set `""` for none.
*   `javaPackageSuffix`: Appended to the POJO's Java package to form `option java_package`: the Java classes generated for POJOs in `com.example.dtos` go into `com.example.dtos.proto`. With `usePojoPackages` the suffix is also part of the Protobuf `package` and of the directory, so a file's location, `package` and `java_package` always match. With the flat layout it only changes `java_package` (`packageName` + suffix). Must start with `.`. Defaults to `".proto"`; set `""` to keep the `.proto` files in exactly the POJO's package.
    > **Note:** With `usePojoPackages` and both `nameSuffix` and `javaPackageSuffix` set to `""`, the generated Java classes get exactly the same fully qualified names as your POJOs (`com.example.dtos.Address`), which fails to compile if both are on the same classpath. The task logs a warning in that case.

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

With the default settings, the `pojoToProto` task generates `src/main/proto/com/example/pojo/proto/UserProto.proto`:

```protobuf
syntax = "proto3";

package com.example.pojo.proto;

option java_package = "com.example.pojo.proto";
option java_multiple_files = true;

message UserProto {
  string name = 1;
  int32 age = 2;
}
```

The generated Java class is `com.example.pojo.proto.UserProto`, so it can live next to the POJO `com.example.pojo.User`.

### Types from Other Packages

Given these POJOs:

```java
package com.example.enums;

public enum OrderStatus { ACTIVE, CANCELLED }
```

```java
package com.example.dtos;

import com.example.enums.OrderStatus;

public class Order {
    private Address shipTo;
    private OrderStatus status;
}
```

the task generates `com/example/enums/proto/OrderStatusProto.proto`, `com/example/dtos/proto/AddressProto.proto` and `com/example/dtos/proto/OrderProto.proto`:

```protobuf
syntax = "proto3";

package com.example.dtos.proto;

option java_package = "com.example.dtos.proto";
option java_multiple_files = true;

import "com/example/dtos/proto/AddressProto.proto";
import "com/example/enums/proto/OrderStatusProto.proto";

message OrderProto {
  AddressProto shipTo = 1;
  com.example.enums.proto.OrderStatusProto status = 2;
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
