package com.trimlink.module.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

@Data
public class BreakTimeRequest {

    @Size(max = 100)
    private String label;

    @NotNull(message = "Break start time is required")
    private LocalTime startTime;

    @NotNull(message = "Break end time is required")
    private LocalTime endTime;
}
