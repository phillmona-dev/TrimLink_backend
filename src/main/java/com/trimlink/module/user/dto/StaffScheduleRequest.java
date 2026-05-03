package com.trimlink.module.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
public class StaffScheduleRequest {

    @NotNull(message = "Day of week is required")
    private DayOfWeek dayOfWeek;

    private LocalTime startTime;
    private LocalTime endTime;

    /** Set to true to mark staff as unavailable for this day. */
    private boolean dayOff = false;
}
