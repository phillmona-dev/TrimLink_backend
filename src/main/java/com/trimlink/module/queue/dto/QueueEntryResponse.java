package com.trimlink.module.queue.dto;

import com.trimlink.module.queue.entity.QueueStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** A single entry in the shop/barber queue dashboard view. */
@Data
@Builder
public class QueueEntryResponse {

    private UUID entryId;
    private String customerName;
    private String customerPhone;
    private String serviceName;
    private int durationMinutes;
    private QueueStatus status;
    private int position;
    private LocalDateTime joinedAt;
    private int waitedMinutes;
}
