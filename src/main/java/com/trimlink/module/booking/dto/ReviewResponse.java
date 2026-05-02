package com.trimlink.module.booking.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ReviewResponse {
    private UUID reviewId;
    private UUID appointmentId;
    private UUID barberId;
    private String barberName;
    private UUID reviewerId;
    private String reviewerName;
    private BigDecimal rating;
    private String comment;
    private LocalDateTime createdAt;
}
