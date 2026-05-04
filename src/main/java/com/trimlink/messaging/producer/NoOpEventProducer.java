package com.trimlink.messaging.producer;

import com.trimlink.messaging.event.BookingCreatedEvent;
import com.trimlink.messaging.event.BookingConfirmedEvent;
import com.trimlink.messaging.event.OtpRequestedEvent;
import com.trimlink.messaging.event.PaymentEvent;
import com.trimlink.messaging.event.QueueUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * No-op implementation of EventProducer for environments where Kafka is disabled.
 * Only active when trimlink.kafka.enabled=false (default).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "trimlink.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEventProducer implements EventProducer {

    @Override
    public void publishBookingCreated(BookingCreatedEvent event) {
        log.debug("Kafka disabled. Skipping publishBookingCreated for ID: {}", event.getAppointmentId());
    }

    @Override
    public void publishBookingConfirmed(BookingConfirmedEvent event) {
        log.debug("Kafka disabled. Skipping publishBookingConfirmed for ID: {}", event.getAppointmentId());
    }

    @Override
    public void publishBookingCancelled(UUID appointmentId) {
        log.debug("Kafka disabled. Skipping publishBookingCancelled for ID: {}", appointmentId);
    }

    @Override
    public void publishBookingCompleted(UUID appointmentId) {
        log.debug("Kafka disabled. Skipping publishBookingCompleted for ID: {}", appointmentId);
    }

    @Override
    public void publishPaymentSuccess(PaymentEvent event) {
        log.debug("Kafka disabled. Skipping publishPaymentSuccess for ref: {}", event.getTxRef());
    }

    @Override
    public void publishPaymentFailed(PaymentEvent event) {
        log.debug("Kafka disabled. Skipping publishPaymentFailed for ref: {}", event.getTxRef());
    }

    @Override
    public void publishQueueUpdated(QueueUpdatedEvent event) {
        log.debug("Kafka disabled. Skipping publishQueueUpdated for entry: {}", event.getEntryId());
    }

    @Override
    public void publishOtpRequested(OtpRequestedEvent event) {
        log.debug("Kafka disabled. Skipping publishOtpRequested for phone: {}", event.getPhoneNumber());
    }
}
