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

import io.github.anupambasak.gradle.testenums.PnrConstants;
import io.github.anupambasak.gradle.testenums.PnrConstants.TxnType;
import lombok.Data;

import java.util.List;

/**
 * Uses enums nested in {@link PnrConstants} both by simple (imported) name and by qualified name,
 * from a different package than the one declaring them.
 */
@Data
public class PnrSessionPojo {
    private int sessionSrlNumber;
    private TxnType txnType;
    private PnrConstants.PnrStatus pnrStatus;
    private List<TxnType> txnHistory;
}
