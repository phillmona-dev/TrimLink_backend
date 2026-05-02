package com.trimlink.module.queue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Request to join the walk-in queue. Supports offline sync via clientTimestamp. */
@Data
public class JoinQueueRequest {

    @NotNull(message = "Barber ID is required")
    private UUID barberId;

    @NotNull(message = "Shop ID is required")
    private UUID shopId;

    @NotNull(message = "Service ID is required")
    private UUID serviceId;

    /**
     * Client's local clock at the moment the user tapped "Join Queue".
     * Used for offline sync: when the request arrives late (after offline period),
     * this timestamp determines fair FIFO ordering instead of server arrival time.
     * If null, server uses current time.
     */
    private LocalDateTime clientTimestamp;

    private String notes;
}
