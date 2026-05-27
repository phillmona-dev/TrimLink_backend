package com.trimlink.messaging.producer;

import com.trimlink.messaging.event.BookingCreatedEvent;
import com.trimlink.messaging.event.BookingConfirmedEvent;
import com.trimlink.messaging.event.OtpRequestedEvent;
import com.trimlink.messaging.event.PaymentEvent;
import com.trimlink.messaging.event.QueueUpdatedEvent;

import java.util.UUID;

public interface EventProducer {
    void publishBookingCreated(BookingCreatedEvent event);
    void publishBookingConfirmed(BookingConfirmedEvent event);
    void publishBookingCancelled(com.trimlink.messaging.event.BookingCancelledEvent event);
    void publishBookingCompleted(UUID appointmentId);
    void publishPaymentSuccess(PaymentEvent event);
    void publishPaymentFailed(PaymentEvent event);
    void publishQueueUpdated(QueueUpdatedEvent event);
    void publishOtpRequested(OtpRequestedEvent event);
}
