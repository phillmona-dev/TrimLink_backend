package com.trimlink.module.admin.dto;

import com.trimlink.module.payment.entity.PaymentStatus;
import com.trimlink.module.payment.entity.PaymentProvider;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private UUID id;
    private String shopName;
    private String customerName;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentProvider provider;
    private String txRef;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
