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

package io.github.anupambasak.gradle.dtos.base;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Abstract superclass in another package than its subclasses: its fields (and its nested enum) are flattened
 * into the messages of {@code CustomerPojo} and {@code VipCustomerPojo}.
 */
@Getter
@Setter
public abstract class AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Origin { WEB, API }

    private String id;
    private Instant createdAt;
    private Origin origin;
}
