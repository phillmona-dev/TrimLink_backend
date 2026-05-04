package com.trimlink.module.booking.dto;

import com.trimlink.module.booking.entity.AppointmentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Outbound appointment response — serialized richly to avoid extra API calls. */
@Data
@Builder
public class AppointmentResponse {

    private UUID id;

    private UUID customerId;
    private String customerName;
    private String customerPhone;

    private UUID barberId;
    private String barberName;

    private UUID shopId;
    private String shopName;
    private String shopAddress;

    private UUID serviceId;
    private String serviceName;
    private Integer serviceDurationMinutes;

    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;

    private AppointmentStatus status;
    private com.trimlink.module.payment.entity.PaymentStatus paymentStatus;
    private BigDecimal priceCharged;
    private String notes;
    private String cancellationReason;
    private String receiptImageUrl;
    private String ticketNumber;

    private LocalDateTime createdAt;
    private boolean reviewed;
}
