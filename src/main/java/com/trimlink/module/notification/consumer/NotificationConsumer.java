package com.trimlink.module.notification.consumer;

import com.trimlink.messaging.event.BookingCreatedEvent;
import com.trimlink.messaging.event.BookingConfirmedEvent;
import com.trimlink.messaging.event.OtpRequestedEvent;
import com.trimlink.messaging.event.PaymentEvent;
import com.trimlink.messaging.event.QueueUpdatedEvent;
import com.trimlink.module.notification.dto.PushMessage;
import com.trimlink.module.notification.service.PushNotificationService;
import com.trimlink.module.notification.service.SmsService;
import com.trimlink.module.notification.service.WebSocketNotificationService;
import com.trimlink.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka consumer that translates domain events into actionable notifications.
 *
 * Each listener runs in its own thread (configured by the consumer group).
 * Failures are logged and the message is committed — a dead-letter topic
 * (trimlink.*.dlq) can be added for retry/alerting in production.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final SmsService smsService;
    private final PushNotificationService pushNotificationService;
    private final UserRepository userRepository;
    private final WebSocketNotificationService webSocketNotificationService;

    @KafkaListener(
            topics = "${trimlink.kafka.topics.booking-created}",
            groupId = "trimlink-notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onBookingCreated(@Payload BookingCreatedEvent event,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("Notification: BookingCreated for appointmentId={}", event.getAppointmentId());
        try {
            String message = String.format(
                    "TrimLink: Hi %s, your appointment with %s at %s is pending confirmation for %s. " +
                    "Price: %.2f ETB.",
                    event.getCustomerName(),
                    event.getBarberName(),
                    event.getShopName(),
                    event.getScheduledStart(),
                    event.getPriceCharged()
            );
            smsService.send(event.getCustomerPhone(), message);
            pushNotificationService.sendToUser(event.getCustomerId(), PushMessage.builder()
                    .title("Booking Pending")
                    .body(String.format("%s with %s at %s", event.getServiceName(), event.getBarberName(), event.getScheduledStart()))
                    .data(Map.of(
                            "type", "BOOKING_CREATED",
                            "appointmentId", event.getAppointmentId().toString(),
                            "scheduledStart", event.getScheduledStart().toString()
                    ))
                    .build());
                    
            // Notify Barber via WebSocket
            webSocketNotificationService.notifyBarber(event.getBarberId(), Map.of(
                    "type", "BOOKING_CREATED",
                    "appointmentId", event.getAppointmentId().toString(),
                    "customerName", event.getCustomerName(),
                    "serviceName", event.getServiceName(),
                    "scheduledStart", event.getScheduledStart().toString()
            ));
        } catch (Exception e) {
            log.error("Failed to send booking SMS/WebSocket for appointmentId={}: {}",
                    event.getAppointmentId(), e.getMessage());
        }
    }

    @KafkaListener(
            topics = "${trimlink.kafka.topics.booking-confirmed}",
            groupId = "trimlink-notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onBookingConfirmed(@Payload BookingConfirmedEvent event) {
        log.info("Notification: BookingConfirmed for appointmentId={}", event.getAppointmentId());
        try {
            String message = String.format(
                    "TrimLink: Good news %s! Your booking at %s is APPROVED. Your virtual ticket is %s. " +
                    "See you at %s.",
                    event.getCustomerName(),
                    event.getShopName(),
                    event.getTicketNumber(),
                    event.getScheduledStart()
            );
            
            smsService.send(event.getCustomerPhone(), message);
            
            pushNotificationService.sendToUser(event.getCustomerId(), PushMessage.builder()
                    .title("Booking Approved! " + event.getTicketNumber())
                    .body(String.format("Your booking at %s is ready. Ticket: %s", event.getShopName(), event.getTicketNumber()))
                    .data(Map.of(
                            "type", "BOOKING_CONFIRMED",
                            "appointmentId", event.getAppointmentId().toString(),
                            "ticketNumber", event.getTicketNumber()
                    ))
                    .build());

            // Notify Customer via WebSocket
            webSocketNotificationService.notifyCustomer(event.getCustomerId(), Map.of(
                    "type", "BOOKING_CONFIRMED",
                    "appointmentId", event.getAppointmentId().toString(),
                    "ticketNumber", event.getTicketNumber(),
                    "message", message
            ));
        } catch (Exception e) {
            log.error("Failed to send confirmation notification for appointmentId={}: {}",
                    event.getAppointmentId(), e.getMessage());
        }
    }

    @KafkaListener(
            topics = "${trimlink.kafka.topics.queue-updated}",
            groupId = "trimlink-notification-group"
    )
    public void onQueueUpdated(@Payload QueueUpdatedEvent event) {
        log.info("Notification: QueueUpdated type={} for entryId={}", event.getEventType(), event.getEntryId());
        try {
            String message = buildQueueMessage(event);
            if (message != null) {
                smsService.send(event.getCustomerPhone(), message);
                pushNotificationService.sendToUser(event.getCustomerId(), PushMessage.builder()
                        .title(queuePushTitle(event.getEventType()))
                        .body(message)
                        .data(Map.of(
                                "type", "QUEUE_" + event.getEventType(),
                                "queueEntryId", event.getEntryId().toString(),
                                "shopName", event.getShopName()
                        ))
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to send queue SMS for entryId={}: {}", event.getEntryId(), e.getMessage());
        }
    }

    @KafkaListener(
            topics = "${trimlink.kafka.topics.payment-success}",
            groupId = "trimlink-notification-group"
    )
    public void onPaymentSuccess(@Payload PaymentEvent event) {
        log.info("Notification: PaymentSuccess for txRef={}", event.getTxRef());
        // Payment success notification handled via booking-created event (already sent)
    }

    @KafkaListener(
            topics = "${trimlink.kafka.topics.payment-failed}",
            groupId = "trimlink-notification-group"
    )
    public void onPaymentFailed(@Payload PaymentEvent event) {
        log.info("Notification: PaymentFailed for txRef={}", event.getTxRef());
        try {
            String message = String.format(
                    "TrimLink: Your payment of %.2f ETB via %s failed. Please try again.",
                    event.getAmount(), event.getProvider());
            userRepository.findById(event.getUserId())
                    .map(user -> user.getPhoneNumber())
                    .ifPresentOrElse(
                            phone -> {
                                smsService.send(phone, message);
                                pushNotificationService.sendToUser(event.getUserId(), PushMessage.builder()
                                        .title("Payment Failed")
                                        .body(message)
                                        .data(Map.of(
                                                "type", "PAYMENT_FAILED",
                                                "paymentId", event.getPaymentId().toString(),
                                                "txRef", event.getTxRef()
                                        ))
                                        .build());
                            },
                            () -> log.warn("Skipping payment failure SMS. User {} not found.", event.getUserId())
                    );
        } catch (Exception e) {
            log.error("Failed to send payment failure SMS: {}", e.getMessage());
        }
    }

    @KafkaListener(
            topics = "${trimlink.kafka.topics.otp-requested}",
            groupId = "trimlink-notification-group"
    )
    public void onOtpRequested(@Payload OtpRequestedEvent event) {
        log.info("Notification: OtpRequested for phone={}", event.getPhoneNumber());
        try {
            String message = String.format(
                    "TrimLink: Your verification code is %s. It expires in %d minutes.",
                    event.getOtpCode(), event.getTtlMinutes());
            smsService.send(event.getPhoneNumber(), message);
        } catch (Exception e) {
            log.error("Failed to send OTP SMS for phone {}: {}", event.getPhoneNumber(), e.getMessage());
        }
    }

    private String buildQueueMessage(QueueUpdatedEvent event) {
        return switch (event.getEventType()) {
            case "JOINED" -> String.format(
                    "TrimLink: You joined the queue at %s. Position: #%d. Est. wait: %d min.",
                    event.getShopName(), event.getPosition(), event.getEstimatedWaitMinutes());
            case "CALLED" -> String.format(
                    "TrimLink: %s is calling you next! Please proceed to the barber now.",
                    event.getShopName());
            case "CANCELLED" -> String.format(
                    "TrimLink: Your queue entry at %s has been cancelled.", event.getShopName());
            default -> null;
        };
    }

    private String queuePushTitle(String eventType) {
        return switch (eventType) {
            case "JOINED" -> "Queue Joined";
            case "CALLED" -> "Your Turn";
            case "CANCELLED" -> "Queue Cancelled";
            case "SERVICE_STARTED" -> "Service Started";
            case "COMPLETED" -> "Service Completed";
            default -> "Queue Update";
        };
    }
}
