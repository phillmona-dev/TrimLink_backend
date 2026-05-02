package com.trimlink.module.booking.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** A single available time slot returned by the slot generator. */
@Data
@Builder
public class TimeSlotResponse {
    private LocalDateTime start;
    private LocalDateTime end;
    private boolean available;
}
