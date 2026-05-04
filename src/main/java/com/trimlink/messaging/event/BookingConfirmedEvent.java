package com.trimlink.messaging.event;

import com.trimlink.module.booking.entity.Appointment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingConfirmedEvent {
    private UUID appointmentId;
    private UUID customerId;
    private String customerPhone;
    private String customerName;
    private UUID barberId;
    private String barberName;
    private String shopName;
    private String serviceName;
    private String ticketNumber;
    private LocalDateTime scheduledStart;
    private LocalDateTime occurredAt;

    public static BookingConfirmedEvent from(Appointment a) {
        return BookingConfirmedEvent.builder()
                .appointmentId(a.getId())
                .customerId(a.getCustomer().getId())
                .customerPhone(a.getCustomer().getPhoneNumber())
                .customerName(a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName())
                .barberId(a.getBarber().getId())
                .barberName(a.getBarber().getUser().getFirstName() + " " + a.getBarber().getUser().getLastName())
                .shopName(a.getShop().getName())
                .serviceName(a.getService().getName())
                .ticketNumber(a.getTicketNumber())
                .scheduledStart(a.getScheduledStart())
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
