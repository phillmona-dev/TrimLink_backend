package com.trimlink.messaging.event;

import com.trimlink.module.booking.entity.Appointment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingCreatedEvent {
    private UUID appointmentId;
    private UUID customerId;
    private String customerPhone;
    private String customerName;
    private UUID barberId;
    private String barberName;
    private String shopName;
    private String serviceName;
    private LocalDateTime scheduledStart;
    private BigDecimal priceCharged;
    private LocalDateTime occurredAt;

    public static BookingCreatedEvent from(Appointment a) {
        return BookingCreatedEvent.builder()
                .appointmentId(a.getId())
                .customerId(a.getCustomer().getId())
                .customerPhone(a.getCustomer().getPhoneNumber())
                .customerName(a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName())
                .barberId(a.getBarber().getId())
                .barberName(a.getBarber().getUser().getFirstName() + " " + a.getBarber().getUser().getLastName())
                .shopName(a.getShop().getName())
                .serviceName(a.getService().getName())
                .scheduledStart(a.getScheduledStart())
                .priceCharged(a.getPriceCharged())
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
