package com.trimlink.module.shop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPerformanceResponse {
    private UUID barberId;
    private String barberName;
    private int totalCustomers;
    private int appBookings;
    private int manualEntries;
    private double dailyAverage;
}
