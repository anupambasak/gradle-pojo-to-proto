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

import lombok.Data;

import java.time.LocalDateTime;

/** Classes nested in a POJO are generated as messages nested in its message. */
@Data
public class TravelPolicyRequestDto {

    private String pnrNo;
    private String status;
    private LocalDateTime policyIssueDate;
    private String txnNo;
    private MessageResult messageResult;
    private PassengerDetailsDto passengerDetailsDto;
    private PassengerDetailsDto.Coverage primaryCoverage;

    @Data
    public class MessageResult {
        private String statusDescription;
        private String statusCode;
    }

    @Data
    public static class PassengerDetailsDto {
        private String policyNo;
        private Integer passengerId;
        private Coverage coverage;
        private java.util.List<Coverage> extraCoverages;

        @Data
        public static class Coverage {
            private long amount;
            private Kind kind;
        }

        public enum Kind { MEDICAL, BAGGAGE }
    }
}
