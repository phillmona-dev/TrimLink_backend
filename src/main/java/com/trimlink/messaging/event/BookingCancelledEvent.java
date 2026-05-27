package com.trimlink.messaging.event;

import com.trimlink.module.booking.entity.Appointment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingCancelledEvent {
    private UUID appointmentId;
    private UUID customerId;
    private String customerPhone;
    private String customerName;
    private String shopName;
    private String reason;
    private LocalDateTime occurredAt;

    public static BookingCancelledEvent from(Appointment a) {
        return BookingCancelledEvent.builder()
                .appointmentId(a.getId())
                .customerId(a.getCustomer().getId())
                .customerPhone(a.getCustomer().getPhoneNumber())
                .customerName(a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName())
                .shopName(a.getShop().getName())
                .reason(a.getCancellationReason())
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
