package com.trimlink.module.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DashboardStats {
    private long totalUsers;
    private long totalBarbers;
    private long totalShops;
    private long totalAppointmentsToday;
    private long totalAppointmentsThisMonth;
    private long activeQueueEntries;
    private BigDecimal revenueToday;
    private BigDecimal revenueThisMonth;
    private long completedServicesToday;
    private long pendingAppointments;
}
