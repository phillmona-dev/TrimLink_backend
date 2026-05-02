package com.trimlink.module.notification.consumer;

import com.trimlink.messaging.event.BookingCreatedEvent;
import com.trimlink.messaging.event.OtpRequestedEvent;
import com.trimlink.messaging.event.PaymentEvent;
import com.trimlink.messaging.event.QueueUpdatedEvent;
import com.trimlink.module.notification.service.PushNotificationService;
import com.trimlink.module.notification.service.SmsService;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private SmsService smsService;

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @Test
    void onBookingCreatedShouldSendSmsAndPush() {
        UUID customerId = UUID.randomUUID();
        BookingCreatedEvent event = BookingCreatedEvent.builder()
                .appointmentId(UUID.randomUUID())
                .customerId(customerId)
                .customerPhone("+251911111111")
                .customerName("Sara Alemu")
                .barberName("Miki")
                .shopName("Trim House")
                .serviceName("Haircut")
                .scheduledStart(LocalDateTime.of(2026, 5, 1, 10, 0))
                .priceCharged(new BigDecimal("180.00"))
                .build();

        notificationConsumer.onBookingCreated(event, "trimlink.booking.created");

        verify(smsService).send(eq("+251911111111"), any(String.class));
        verify(pushNotificationService).sendToUser(eq(customerId), any());
    }

    @Test
    void onQueueUpdatedShouldSendPushWhenMessageCanBeBuilt() {
        UUID customerId = UUID.randomUUID();
        QueueUpdatedEvent event = QueueUpdatedEvent.builder()
                .entryId(UUID.randomUUID())
                .customerId(customerId)
                .customerPhone("+251922222222")
                .shopName("Trim House")
                .eventType("CALLED")
                .build();

        notificationConsumer.onQueueUpdated(event);

        verify(smsService).send(eq("+251922222222"), any(String.class));
        verify(pushNotificationService).sendToUser(eq(customerId), any());
    }

    @Test
    void onPaymentFailedShouldLookUpPhoneAndSendBothChannels() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .phoneNumber("+251933333333")
                .firstName("Mahi")
                .lastName("Birru")
                .role(Role.CUSTOMER)
                .build();

        PaymentEvent event = PaymentEvent.builder()
                .paymentId(UUID.randomUUID())
                .userId(userId)
                .txRef("TRIM-PAY-1")
                .amount(new BigDecimal("200.00"))
                .provider("CHAPA")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        notificationConsumer.onPaymentFailed(event);

        verify(smsService).send(eq("+251933333333"), any(String.class));
        verify(pushNotificationService).sendToUser(eq(userId), any());
    }

    @Test
    void onOtpRequestedShouldOnlySendSms() {
        OtpRequestedEvent event = OtpRequestedEvent.builder()
                .phoneNumber("+251944444444")
                .otpCode("123456")
                .ttlMinutes(5)
                .build();

        notificationConsumer.onOtpRequested(event);

        verify(smsService).send(eq("+251944444444"), any(String.class));
        verify(pushNotificationService, never()).sendToUser(any(), any());
    }
}
