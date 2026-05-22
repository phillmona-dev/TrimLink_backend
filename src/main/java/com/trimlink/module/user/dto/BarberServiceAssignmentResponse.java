package com.trimlink.module.user.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BarberServiceAssignmentResponse {
    private UUID assignmentId;
    private UUID barberId;
    private UUID serviceId;
    private String serviceName;
    private String serviceDescription;
    private Integer durationMinutes;
    private BigDecimal basePrice;
    private BigDecimal customPrice;
    private BigDecimal effectivePrice;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Parsed list of haircut style image URLs the barber has attached to this service. */
    private List<String> styleImageUrls;
}
