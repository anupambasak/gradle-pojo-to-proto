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

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GradlePojoToProtoPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        PojoToProtoExtension extension = project.getExtensions().create("pojoToProto", PojoToProtoExtension.class);

        project.getTasks().register("pojoToProto", PojoToProtoTask.class, task -> {
            task.getSource().set(extension.getSource());
            task.getDestination().set(extension.getDestination());
            task.getSingleFile().set(extension.getSingleFile());
            task.getPackageName().set(extension.getPackageName());
            task.getProjectName().set(project.getName());
            task.getProjectGroup().set(project.getGroup().toString());
        });
    }
}
