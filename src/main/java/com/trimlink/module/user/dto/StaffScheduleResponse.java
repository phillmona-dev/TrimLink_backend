package com.trimlink.module.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StaffScheduleResponse {
    private UUID id;
    private UUID staffId;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean dayOff;
    private List<BreakTimeDto> breakTimes;

    @Data
    @Builder
    public static class BreakTimeDto {
        private UUID id;
        private String label;
        private LocalTime startTime;
        private LocalTime endTime;
    }
}
