package com.trimlink.module.payment.dto;

import com.trimlink.module.payment.entity.PaymentProvider;
import com.trimlink.module.payment.entity.PaymentReferenceType;
import com.trimlink.module.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PaymentResponse {
    private UUID id;
    private String txRef;
    private PaymentProvider provider;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String checkoutUrl;
    private UUID referenceId;
    private PaymentReferenceType referenceType;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
