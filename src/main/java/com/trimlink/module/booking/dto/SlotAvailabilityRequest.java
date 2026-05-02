package com.trimlink.module.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/** Request to generate available time slots for a barber on a given day. */
@Data
public class SlotAvailabilityRequest {

    @NotNull(message = "Barber ID is required")
    private UUID barberId;

    @NotNull(message = "Service ID is required")
    private UUID serviceId;

    @NotNull(message = "Date is required")
    private LocalDate date;
}
