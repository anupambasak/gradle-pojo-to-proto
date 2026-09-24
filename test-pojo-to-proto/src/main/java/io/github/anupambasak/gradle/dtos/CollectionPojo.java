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

package io.github.anupambasak.gradle.dtos;

import io.github.anupambasak.gradle.testenums.TestEnum;
import lombok.Data;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;

/** Sets and other collections become repeated fields, like List. */
@Data
public class CollectionPojo {
    private Set<String> tags;
    private HashSet<Address> addresses;
    private java.util.Set<Integer> codes;
    private SortedSet<Long> ids;
    private Set<TestEnum> flags;
    private Collection<Address> history;
    private LinkedList<String> queue;
}
