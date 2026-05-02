package com.trimlink.module.shop.dto;

import com.trimlink.module.user.dto.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffPerformanceResponse {
    private UserResponse user;
    private UUID barberId;
    private boolean available;
    private int customersToday;
    private int manualLogsToday;
    private int appBookingsToday;
    private double weeklyAverage;
    private int totalReviews;
    private double averageRating;
}
