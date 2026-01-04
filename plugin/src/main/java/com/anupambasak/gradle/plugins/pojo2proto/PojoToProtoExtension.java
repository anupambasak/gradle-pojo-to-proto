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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

import java.util.List;

public abstract class PojoToProtoExtension {
    public abstract ConfigurableFileCollection getSource();
    public abstract DirectoryProperty getDestination();
    public abstract Property<Boolean> getSingleFile();
    public abstract Property<String> getPackageName();
    public abstract Property<List<String>> getExcludeFields();
}
