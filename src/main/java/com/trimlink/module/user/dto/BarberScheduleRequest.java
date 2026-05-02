package com.trimlink.module.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
public class BarberScheduleRequest {

    @NotNull(message = "Day of week is required")
    private DayOfWeek dayOfWeek;

    private LocalTime startTime;
    private LocalTime endTime;

    /** Set to true to mark barber as unavailable for this day. */
    private boolean dayOff = false;
}
