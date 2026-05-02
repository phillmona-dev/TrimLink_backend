package com.trimlink.module.shop.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ShopStatsResponse {
    private String revenueToday;
    private String revenueHelper;
    private int appointmentsToday;
    private String appointmentsHelper;
    private int queueTraffic;
    private String queueHelper;
    private String repeatCustomerRate;
    private String repeatHelper;
    private List<ChartDataPoint> revenueTrend;

    @Data
    @Builder
    public static class ChartDataPoint {
        private String label;
        private double value;
    }
}
