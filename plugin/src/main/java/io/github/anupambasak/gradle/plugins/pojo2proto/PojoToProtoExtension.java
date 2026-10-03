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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

import java.util.List;

public abstract class PojoToProtoExtension {
    public abstract ConfigurableFileCollection getSource();
    /** Directories or files to skip. Any .java file under an excluded directory (or equal to an excluded file) is ignored. */
    public abstract ConfigurableFileCollection getExclude();
    public abstract DirectoryProperty getDestination();
    public abstract Property<Boolean> getSingleFile();
    public abstract Property<Boolean> getPrefixEnumNames();
    /**
     * Proto package (and, with {@link #getJavaPackageSuffix()}, Java package) shared by all generated files.
     * Only used with the flat layout: {@code usePojoPackages = false}, which {@code singleFile} implies.
     * Ignored when {@link #getUsePojoPackages()} is {@code true}, where each file takes its POJO's package.
     * Defaults to the Gradle project's group.
     */
    public abstract Property<String> getPackageName();
    /**
     * When {@code true}, every .proto file gets the Java package of its POJO followed by
     * {@link #getJavaPackageSuffix()} as its proto {@code package} and {@code java_package}, and is written to the
     * matching sub-directory of {@code destination}
     * ({@code com.example.dtos.Address -> <destination>/com/example/dtos/proto/AddressProto.proto}). Imports use these
     * paths and references across packages are fully qualified. Cannot be combined with {@code singleFile}.
     * <p>
     * Defaults to {@code true}, or {@code false} when {@code singleFile} is enabled. When {@code false}, all files
     * are written flat into {@code destination} and share {@link #getPackageName()} as their proto package.
     */
    public abstract Property<Boolean> getUsePojoPackages();
    /**
     * Appended to every generated top-level message and enum name and to its file name:
     * {@code Address -> message AddressProto} in {@code AddressProto.proto}. Defaults to {@code "Proto"};
     * set to {@code ""} for none.
     */
    public abstract Property<String> getNameSuffix();
    /**
     * Appended to the POJO's Java package to form {@code option java_package}: the Java classes for POJOs in
     * {@code com.example.dtos} are generated into {@code com.example.dtos.proto}. With {@link #getUsePojoPackages()}
     * it is also part of the proto {@code package} and directory, so they match {@code java_package}; with the
     * flat layout it only applies to {@code java_package}. Defaults to {@code ".proto"}; set to {@code ""} for none.
     */
    public abstract Property<String> getJavaPackageSuffix();
    /**
     * When {@code true} (the default), every singular field is generated as {@code optional} (proto3 explicit
     * presence), so a field that was never set can be told apart from one set to {@code 0}, {@code false},
     * {@code ""} or the first enum value: the generated Java has {@code hasX()}/{@code clearX()}, and a POJO
     * {@code null} can be mapped to "not set". {@code repeated} and {@code map} fields cannot be optional and are
     * unaffected. Set to {@code false} for plain proto3 fields.
     */
    public abstract Property<Boolean> getOptionalFields();
    public abstract Property<List<String>> getExcludeFields();
}
