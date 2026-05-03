package com.trimlink.module.queue.dto;

import com.trimlink.module.queue.entity.QueueStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Rich queue ticket returned after joining or querying the queue. */
@Data
@Builder
public class QueueTicketResponse {

    private UUID entryId;

    private UUID customerId;
    private String customerName;
    private String customerPhone;

    private UUID staffId;
    private String staffName;

    private UUID shopId;
    private String shopName;

    private UUID serviceId;
    private String serviceName;
    private Integer serviceDurationMinutes;

    private QueueStatus status;

    /** 1-based position in the queue (computed at query time). */
    private int position;

    /** Estimated wait time in minutes before this customer is served. */
    private int estimatedWaitMinutes;

    private LocalDateTime joinedAt;
    private LocalDateTime calledAt;
    private LocalDateTime serviceStartedAt;
}
