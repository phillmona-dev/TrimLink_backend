package com.trimlink.module.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class StaffPerformanceResponse {
    private UUID staffId;
    private UUID userId;
    private String staffName;
    private String phoneNumber;
    private UUID shopId;
    private String shopName;
    private boolean available;
    private BigDecimal averageRating;
    private int totalReviews;
    private long completedAppointmentsToday;
    private long completedAppointmentsThisMonth;
    private long pendingAppointments;
    private long activeQueueEntries;
    private long completedQueueServicesToday;
}
