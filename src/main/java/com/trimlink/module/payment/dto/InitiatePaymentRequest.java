package com.trimlink.module.payment.dto;

import com.trimlink.module.payment.entity.PaymentProvider;
import com.trimlink.module.payment.entity.PaymentReferenceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class InitiatePaymentRequest {

    @NotNull(message = "Reference ID is required")
    private UUID referenceId;

    @NotNull(message = "Reference type is required")
    private PaymentReferenceType referenceType;

    @NotNull(message = "Provider is required")
    private PaymentProvider provider;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.0", message = "Amount must be at least 1 ETB")
    private BigDecimal amount;
}
