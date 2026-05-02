package com.trimlink.messaging.event;

import com.trimlink.module.payment.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentEvent {
    private UUID paymentId;
    private UUID userId;
    private String txRef;
    private BigDecimal amount;
    private String currency;
    private String provider;
    private String status;       // SUCCESS | FAILED
    private UUID referenceId;
    private String referenceType;
    private LocalDateTime occurredAt;

    public static PaymentEvent success(Payment p) {
        return builder()
                .paymentId(p.getId())
                .userId(p.getUser().getId())
                .txRef(p.getTxRef())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .provider(p.getProvider().name())
                .status("SUCCESS")
                .referenceId(p.getReferenceId())
                .referenceType(p.getReferenceType().name())
                .occurredAt(LocalDateTime.now())
                .build();
    }

    public static PaymentEvent failed(Payment p) {
        return builder()
                .paymentId(p.getId())
                .userId(p.getUser().getId())
                .txRef(p.getTxRef())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .provider(p.getProvider().name())
                .status("FAILED")
                .referenceId(p.getReferenceId())
                .referenceType(p.getReferenceType().name())
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
