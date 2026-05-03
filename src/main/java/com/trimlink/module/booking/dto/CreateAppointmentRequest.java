package com.trimlink.module.booking.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Inbound request to create a new appointment. */
@Data
public class CreateAppointmentRequest {

    @NotNull(message = "Staff ID is required")
    private UUID staffId;

    @NotNull(message = "Shop ID is required")
    private UUID shopId;

    @NotNull(message = "Service ID is required")
    private UUID serviceId;

    @NotNull(message = "Scheduled start time is required")
    @Future(message = "Appointment must be in the future")
    private LocalDateTime scheduledStart;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
}
