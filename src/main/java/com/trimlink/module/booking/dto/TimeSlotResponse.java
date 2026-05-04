package com.trimlink.module.booking.dto;

import com.trimlink.module.booking.entity.AppointmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** A single available time slot returned by the slot generator. */
@Data
@Builder
public class TimeSlotResponse {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean available;
    private AppointmentStatus status;
    private UUID appointmentId;
}
