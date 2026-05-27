package com.trimlink.messaging.producer;

import com.trimlink.messaging.event.BookingCreatedEvent;
import com.trimlink.messaging.event.BookingConfirmedEvent;
import com.trimlink.messaging.event.OtpRequestedEvent;
import com.trimlink.messaging.event.PaymentEvent;
import com.trimlink.messaging.event.QueueUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka implementation of EventProducer.
 * Only active when trimlink.kafka.enabled=true.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trimlink.kafka.enabled", havingValue = "true")
public class KafkaEventProducer implements EventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${trimlink.kafka.topics.booking-created}")   private String bookingCreated;
    @Value("${trimlink.kafka.topics.booking-confirmed}") private String bookingConfirmed;
    @Value("${trimlink.kafka.topics.booking-cancelled}") private String bookingCancelled;
    @Value("${trimlink.kafka.topics.booking-completed}") private String bookingCompleted;
    @Value("${trimlink.kafka.topics.payment-success}")   private String paymentSuccess;
    @Value("${trimlink.kafka.topics.payment-failed}")    private String paymentFailed;
    @Value("${trimlink.kafka.topics.queue-updated}")     private String queueUpdated;
    @Value("${trimlink.kafka.topics.otp-requested}")     private String otpRequested;

    @org.springframework.scheduling.annotation.Async
    public void publishBookingCreated(BookingCreatedEvent event) {
        send(bookingCreated, event.getAppointmentId().toString(), event);
    }

    @org.springframework.scheduling.annotation.Async
    public void publishBookingConfirmed(BookingConfirmedEvent event) {
        send(bookingConfirmed, event.getAppointmentId().toString(), event);
    }

    @org.springframework.scheduling.annotation.Async
    public void publishBookingCancelled(com.trimlink.messaging.event.BookingCancelledEvent event) {
        send(bookingCancelled, event.getAppointmentId().toString(), event);
    }

    @org.springframework.scheduling.annotation.Async
    public void publishBookingCompleted(UUID appointmentId) {
        send(bookingCompleted, appointmentId.toString(), appointmentId);
    }

    @org.springframework.scheduling.annotation.Async
    public void publishPaymentSuccess(PaymentEvent event) {
        send(paymentSuccess, event.getTxRef(), event);
    }

    @org.springframework.scheduling.annotation.Async
    public void publishPaymentFailed(PaymentEvent event) {
        send(paymentFailed, event.getTxRef(), event);
    }

    @org.springframework.scheduling.annotation.Async
    public void publishQueueUpdated(QueueUpdatedEvent event) {
        send(queueUpdated, event.getEntryId().toString(), event);
    }

    @org.springframework.scheduling.annotation.Async
    public void publishOtpRequested(OtpRequestedEvent event) {
        send(otpRequested, event.getPhoneNumber(), event);
    }

    private void send(String topic, String key, Object payload) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic={}, key={}: {}", topic, key, ex.getMessage());
            } else {
                log.debug("Event published: topic={}, key={}, offset={}",
                        topic, key, result.getRecordMetadata().offset());
            }
        });
    }
}
