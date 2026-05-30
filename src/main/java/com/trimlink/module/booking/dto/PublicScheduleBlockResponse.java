package com.trimlink.module.booking.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Privacy-safe schedule block for customer booking UI (time range only). */
@Data
@Builder
public class PublicScheduleBlockResponse {
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
}
