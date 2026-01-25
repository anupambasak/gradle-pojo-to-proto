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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.List;

@Data
public class TimePojo {
    private Instant instant;
    private ZonedDateTime zonedDateTime;
    private LocalDateTime localDateTime;
    private LocalDate localDate;
    private LocalTime localTime;
    private Duration duration;
    private Period period;
    private List<Instant> instants;
    private List<ZonedDateTime> zonedDateTimes;
    private List<LocalDateTime> localDateTimes;
    private List<LocalDate> localDates;
    private List<LocalTime> localTimes;
    private List<Duration> durations;
    private List<Period> periods;
}
