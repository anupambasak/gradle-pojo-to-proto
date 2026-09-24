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

package io.github.anupambasak.gradle.testenums;

import java.util.EnumSet;
import java.util.HashMap;

/**
 * Enums packed inside an interface. They are generated inside PnrConstants.proto and must be
 * referenced from other messages as {@code PnrConstants.<Enum>}.
 */
public interface PnrConstants {

    enum TxnType {
        BOOKING(0),
        CANCELLATION(1),
        MODIFICATION(2);

        private static final HashMap<Integer, TxnType> val = new HashMap<>();

        static {
            for (TxnType a : EnumSet.allOf(TxnType.class)) {
                val.put(a.value, a);
            }
        }

        private final Integer value;

        TxnType(Integer value) {
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }

        public static TxnType valueOf(Integer value) {
            return val.get(value);
        }
    }

    enum PnrStatus {
        FLUSHED(0),
        BOOKED(1);

        private static final HashMap<Integer, PnrStatus> val = new HashMap<>();

        static {
            for (PnrStatus a : EnumSet.allOf(PnrStatus.class)) {
                val.put(a.value, a);
            }
        }

        private final Integer value;

        PnrStatus(Integer value) {
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }

        public static PnrStatus valueOf(Integer value) {
            return val.get(value);
        }
    }
}
