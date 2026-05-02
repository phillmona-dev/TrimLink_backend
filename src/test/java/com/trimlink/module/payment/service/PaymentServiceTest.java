package com.trimlink.module.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trimlink.common.exception.PaymentException;
import com.trimlink.messaging.producer.EventProducer;
import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.booking.repository.AppointmentRepository;
import com.trimlink.module.booking.service.BookingService;
import com.trimlink.module.payment.client.ChapaClient;
import com.trimlink.module.payment.client.TelebirrClient;
import com.trimlink.module.payment.dto.InitiatePaymentRequest;
import com.trimlink.module.payment.entity.Payment;
import com.trimlink.module.payment.entity.PaymentProvider;
import com.trimlink.module.payment.entity.PaymentReferenceType;
import com.trimlink.module.payment.entity.PaymentStatus;
import com.trimlink.module.payment.repository.PaymentRepository;
import com.trimlink.module.queue.repository.QueueEntryRepository;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository    userRepository;
    @Mock private ChapaClient       chapaClient;
    @Mock private TelebirrClient    telebirrClient;
    @Mock private EventProducer     eventProducer;
    @Mock private BookingService    bookingService;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private QueueEntryRepository queueEntryRepository;
    @Spy  private ObjectMapper      objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentService paymentService;

    private User customer;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        customer = User.builder()
                .firstName("Selam").lastName("Tesfaye")
                .phoneNumber("+251912000003")
                .email("selam@trimlink.et")
                .role(Role.CUSTOMER).build();

        // inject @Value fields
        ReflectionTestUtils.setField(paymentService, "chapaWebhookSecret", "test_webhook_secret");
    }

    // ─── Webhook Handling ──────────────────────────────────────────────────

    @Test
    @DisplayName("Should reject initiation when client amount does not match appointment price")
    void initiatePayment_rejectsAmountMismatch() {
        UUID appointmentId = UUID.randomUUID();

        Appointment appointment = Appointment.builder()
                .customer(customer)
                .priceCharged(new BigDecimal("150.00"))
                .status(AppointmentStatus.PENDING)
                .build();

        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setReferenceId(appointmentId);
        request.setReferenceType(PaymentReferenceType.APPOINTMENT);
        request.setProvider(PaymentProvider.CHAPA);
        request.setAmount(new BigDecimal("200.00"));

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> paymentService.initiatePayment(customerId, request))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("authoritative price");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should initiate Telebirr payment and persist checkout URL")
    void initiatePayment_telebirrSuccess() {
        UUID appointmentId = UUID.randomUUID();

        Appointment appointment = Appointment.builder()
                .customer(customer)
                .priceCharged(new BigDecimal("150.00"))
                .status(AppointmentStatus.PENDING)
                .build();

        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setReferenceId(appointmentId);
        request.setReferenceType(PaymentReferenceType.APPOINTMENT);
        request.setProvider(PaymentProvider.TELEBIRR);
        request.setAmount(new BigDecimal("150.00"));

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(paymentRepository.existsByReferenceIdAndReferenceTypeAndStatusIn(eq(appointmentId), eq(PaymentReferenceType.APPOINTMENT), anyList()))
                .thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(telebirrClient.initiatePayment(any())).thenReturn(TelebirrClient.TelebirrInitResponse.builder()
                .checkoutUrl("https://telebirr.test/pay/123")
                .build());

        var response = paymentService.initiatePayment(customerId, request);

        assertThat(response.getProvider()).isEqualTo(PaymentProvider.TELEBIRR);
        assertThat(response.getCheckoutUrl()).isEqualTo("https://telebirr.test/pay/123");
        verify(telebirrClient).initiatePayment(any());
    }

    @Test
    @DisplayName("Should mark payment SUCCESS and confirm booking on valid Chapa webhook")
    void handleChapaWebhook_success() throws Exception {
        // Arrange
        String txRef = "TRIM-ABCDEF123456";
        String rawPayload = "{\"tx_ref\":\"" + txRef + "\",\"status\":\"success\"}";

        // Compute valid HMAC signature
        String signature = computeHmac("test_webhook_secret", rawPayload);

        Payment pending = Payment.builder()
                .user(customer)
                .txRef(txRef)
                .amount(new BigDecimal("200.00"))
                .provider(PaymentProvider.CHAPA)
                .referenceId(UUID.randomUUID())
                .referenceType(PaymentReferenceType.APPOINTMENT)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByTxRef(txRef)).thenReturn(Optional.of(pending));

        ChapaClient.ChapaVerifyResponse verifyResponse = new ChapaClient.ChapaVerifyResponse();
        verifyResponse.setStatus("success");
        ChapaClient.ChapaVerifyResponse.VerifyData data = new ChapaClient.ChapaVerifyResponse.VerifyData();
        data.setStatus("success");
        data.setReference("chapa_internal_ref_001");
        verifyResponse.setData(data);
        when(chapaClient.verifyTransaction(txRef)).thenReturn(verifyResponse);
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        paymentService.handleChapaWebhook(rawPayload, signature);

        // Assert
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(pending.getProviderTxId()).isEqualTo("chapa_internal_ref_001");
        verify(eventProducer).publishPaymentSuccess(any());
        verify(paymentRepository).save(pending);
    }

    @Test
    @DisplayName("Should reject webhook with invalid HMAC signature")
    void handleChapaWebhook_invalidSignature() {
        String rawPayload = "{\"tx_ref\":\"TRIM-FAKE\",\"status\":\"success\"}";
        String badSignature = "0000000000000000000000000000000000000000000000000000000000000000";

        assertThatThrownBy(() -> paymentService.handleChapaWebhook(rawPayload, badSignature))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Invalid webhook signature");

        verify(paymentRepository, never()).findByTxRef(any());
    }

    @Test
    @DisplayName("Should be idempotent: skip already-processed payment")
    void handleChapaWebhook_idempotency_alreadySuccess() throws Exception {
        String txRef = "TRIM-ALREADY-DONE";
        String rawPayload = "{\"tx_ref\":\"" + txRef + "\"}";
        String signature  = computeHmac("test_webhook_secret", rawPayload);

        Payment already = Payment.builder()
                .txRef(txRef).status(PaymentStatus.SUCCESS)
                .provider(PaymentProvider.CHAPA)
                .referenceId(UUID.randomUUID())
                .referenceType(PaymentReferenceType.APPOINTMENT)
                .user(customer)
                .amount(BigDecimal.TEN).build();

        when(paymentRepository.findByTxRef(txRef)).thenReturn(Optional.of(already));

        // Act — should NOT throw and NOT call verify
        paymentService.handleChapaWebhook(rawPayload, signature);

        // Assert — idempotent: verify is NOT called again
        verify(chapaClient, never()).verifyTransaction(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should mark payment SUCCESS on valid Telebirr webhook")
    void handleTelebirrWebhook_success() {
        String txRef = "TRIM-TELEBIRR-1234";
        Payment pending = Payment.builder()
                .user(customer)
                .txRef(txRef)
                .amount(new BigDecimal("200.00"))
                .provider(PaymentProvider.TELEBIRR)
                .referenceId(UUID.randomUUID())
                .referenceType(PaymentReferenceType.APPOINTMENT)
                .status(PaymentStatus.PENDING)
                .build();

        when(telebirrClient.parseAndVerifyWebhook(anyString(), anyString())).thenReturn(
                TelebirrClient.TelebirrWebhookPayload.builder()
                        .txRef(txRef)
                        .providerTxId("TBX-1")
                        .status("SUCCESS")
                        .amount(new BigDecimal("200.00"))
                        .currency("ETB")
                        .rawPayload("{\"ok\":true}")
                        .build());
        when(paymentRepository.findByTxRef(txRef)).thenReturn(Optional.of(pending));
        when(telebirrClient.verifyTransaction(txRef)).thenReturn(TelebirrClient.TelebirrVerifyResponse.builder()
                .successful(true)
                .providerTxId("TBX-1")
                .txRef(txRef)
                .amount(new BigDecimal("200.00"))
                .currency("ETB")
                .build());
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.handleTelebirrWebhook("{\"payload\":true}", "signature");

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(pending.getProviderTxId()).isEqualTo("TBX-1");
        verify(eventProducer).publishPaymentSuccess(any());
        verify(paymentRepository).save(pending);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private String computeHmac(String secret, String data) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec keySpec =
                new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(data.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
