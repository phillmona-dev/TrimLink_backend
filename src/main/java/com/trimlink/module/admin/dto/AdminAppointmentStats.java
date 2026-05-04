package com.trimlink.module.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AdminAppointmentStats {
    private long totalApproved; // Completed + Confirmed
    private long totalPending;
    private long totalRejected;
    private BigDecimal totalRevenue;
    private BigDecimal revenueToday;
    private BigDecimal adminShare;
    private List<ShopRevenue> shopRevenues;
    private List<BarberRevenue> barberRevenues;

    @Data
    @Builder
    public static class ShopRevenue {
        private UUID shopId;
        private String shopName;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    public static class BarberRevenue {
        private UUID barberId;
        private String barberName;
        private BigDecimal revenue;
    }
}
