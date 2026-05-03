package com.trimlink.messaging.event;

import com.trimlink.module.queue.entity.QueueEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QueueUpdatedEvent {
    private UUID entryId;
    private UUID customerId;
    private String customerPhone;
    private String customerName;
    private UUID staffId;
    private String shopName;
    private String serviceName;
    private String eventType;   // JOINED | CALLED | SERVICE_STARTED | COMPLETED | CANCELLED
    private int position;
    private int estimatedWaitMinutes;
    private LocalDateTime occurredAt;

    public static QueueUpdatedEvent joined(QueueEntry e) {
        return base(e).eventType("JOINED").build();
    }
    public static QueueUpdatedEvent called(QueueEntry e) {
        return base(e).eventType("CALLED").build();
    }
    public static QueueUpdatedEvent serviceStarted(QueueEntry e) {
        return base(e).eventType("SERVICE_STARTED").build();
    }
    public static QueueUpdatedEvent completed(QueueEntry e) {
        return base(e).eventType("COMPLETED").build();
    }
    public static QueueUpdatedEvent cancelled(QueueEntry e) {
        return base(e).eventType("CANCELLED").build();
    }

    private static QueueUpdatedEventBuilder base(QueueEntry e) {
        return QueueUpdatedEvent.builder()
                .entryId(e.getId())
                .customerId(e.getCustomer().getId())
                .customerPhone(e.getCustomer().getPhoneNumber())
                .customerName(e.getCustomer().getFirstName() + " " + e.getCustomer().getLastName())
                .staffId(e.getStaff().getId())
                .shopName(e.getShop().getName())
                .serviceName(e.getService().getName())
                .occurredAt(LocalDateTime.now());
    }
}
