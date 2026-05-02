package com.trimlink.module.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class BarberServiceAssignmentRequest {

    @NotNull(message = "Service ID is required")
    private UUID serviceId;

    @DecimalMin(value = "1.0", message = "Custom price must be at least 1 ETB")
    private BigDecimal customPrice;
}
