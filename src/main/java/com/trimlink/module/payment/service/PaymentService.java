package com.trimlink.module.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trimlink.common.exception.BusinessException;
import com.trimlink.common.exception.PaymentException;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.messaging.event.PaymentEvent;
import com.trimlink.messaging.producer.EventProducer;
import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.booking.repository.AppointmentRepository;
import com.trimlink.module.booking.service.BookingService;
import com.trimlink.module.payment.client.ChapaClient;
import com.trimlink.module.payment.client.TelebirrClient;
import com.trimlink.module.payment.dto.InitiatePaymentRequest;
import com.trimlink.module.payment.dto.PaymentResponse;
import com.trimlink.module.payment.entity.Payment;
import com.trimlink.module.payment.entity.PaymentProvider;
import com.trimlink.module.payment.entity.PaymentReferenceType;
import com.trimlink.module.payment.entity.PaymentStatus;
import com.trimlink.module.payment.repository.PaymentRepository;
import com.trimlink.module.queue.entity.QueueEntry;
import com.trimlink.module.queue.entity.QueueStatus;
import com.trimlink.module.queue.repository.QueueEntryRepository;
import com.trimlink.module.user.entity.BarberServiceAssignment;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment orchestration service.
 *
 * Flow:
 *  1. Client calls POST /payments/initiate
 *  2. We create a Payment record (PENDING) with a unique tx_ref
 *  3. We call Chapa/Telebirr initiation → get checkout_url
 *  4. Return checkout_url to client
 *  5. Chapa/Telebirr POSTs to our webhook (/webhooks/payment/{provider})
 *  6. WebhookController calls handleWebhook() here
 *  7. We verify signature → verify transaction with provider API
 *  8. If confirmed: mark SUCCESS, confirm booking, publish Kafka event
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ChapaClient chapaClient;
    private final TelebirrClient telebirrClient;
    private final EventProducer eventProducer;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;
    private final AppointmentRepository appointmentRepository;
    private final QueueEntryRepository queueEntryRepository;

    @Value("${trimlink.payment.chapa.webhook-secret}")
    private String chapaWebhookSecret;

    // ─── Initiation ────────────────────────────────────────────────────────

    @Transactional
    public PaymentResponse initiatePayment(UUID userId, InitiatePaymentRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        ValidatedPaymentReference reference = validateReference(userId, req);

        // Guard: prevent duplicate PENDING payments for the same reference
        boolean alreadyPending = paymentRepository.existsByReferenceIdAndReferenceTypeAndStatusIn(
                req.getReferenceId(),
                req.getReferenceType(),
                List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS));
        if (alreadyPending) {
            throw new PaymentException("A payment already exists for this reference.");
        }

        // Generate unique tx_ref — our idempotency key sent to the gateway
        String txRef = "TRIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        Payment payment = Payment.builder()
                .user(user)
                .referenceId(req.getReferenceId())
                .referenceType(req.getReferenceType())
                .provider(req.getProvider())
                .amount(reference.amount())
                .txRef(txRef)
                .status(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);

        if (req.getProvider() == PaymentProvider.CHAPA) {
            return initiateChapaPayment(payment, user, req);
        } else {
            return initiateTelebirrPayment(payment, user, req);
        }
    }

    private PaymentResponse initiateChapaPayment(Payment payment, User user, InitiatePaymentRequest req) {
        ChapaClient.ChapaInitRequest chapaReq = new ChapaClient.ChapaInitRequest();
        chapaReq.setAmount(payment.getAmount().toPlainString());
        chapaReq.setTxRef(payment.getTxRef());
        chapaReq.setEmail(user.getEmail() != null ? user.getEmail() : payment.getTxRef() + "@trimlink.et");
        chapaReq.setFirstName(user.getFirstName());
        chapaReq.setLastName(user.getLastName());
        chapaReq.setPhoneNumber(user.getPhoneNumber());
        chapaReq.setCallbackUrl(chapaClient.getCallbackUrl());
        chapaReq.setReturnUrl(chapaClient.getReturnUrl());
        chapaReq.setCustomization(Map.of("title", "TrimLink Payment", "description", "Barbershop service payment"));

        ChapaClient.ChapaInitResponse chapaRes = chapaClient.initiatePayment(chapaReq);

        if (chapaRes == null || chapaRes.getData() == null) {
            throw new PaymentException("Empty response from Chapa gateway.");
        }

        payment.setCheckoutUrl(chapaRes.getData().getCheckoutUrl());
        paymentRepository.save(payment);

        log.info("Chapa payment initiated: txRef={}, checkoutUrl={}", payment.getTxRef(), payment.getCheckoutUrl());
        return toResponse(payment);
    }

    private PaymentResponse initiateTelebirrPayment(Payment payment, User user, InitiatePaymentRequest req) {
        TelebirrClient.TelebirrInitRequest telebirrReq = TelebirrClient.TelebirrInitRequest.builder()
                .txRef(payment.getTxRef())
                .amount(payment.getAmount())
                .phoneNumber(user.getPhoneNumber())
                .title("TrimLink Payment")
                .description("Barbershop service payment")
                .build();

        TelebirrClient.TelebirrInitResponse telebirrRes = telebirrClient.initiatePayment(telebirrReq);
        payment.setCheckoutUrl(telebirrRes.getCheckoutUrl());
        paymentRepository.save(payment);

        log.info("Telebirr payment initiated: txRef={}, checkoutUrl={}", payment.getTxRef(), payment.getCheckoutUrl());
        return toResponse(payment);
    }

    // ─── Webhook Handling (Chapa) ──────────────────────────────────────────

    /**
     * Handles incoming Chapa webhook.
     *
     * Steps:
     *  1. Signature verification — reject requests with invalid HMAC
     *  2. Parse tx_ref from payload
     *  3. Idempotency check — if already processed (SUCCESS/FAILED), return immediately
     *  4. Verify transaction with Chapa API (second-factor confirmation)
     *  5. Update payment status
     *  6. Trigger downstream actions (confirm booking, publish Kafka event)
     */
    @Transactional
    public void handleChapaWebhook(String rawPayload, String signature) {
        // 1. Verify HMAC-SHA256 signature
        if (!verifyHmacSignature(rawPayload, signature, chapaWebhookSecret)) {
            log.warn("Chapa webhook received with invalid signature. Rejected.");
            throw new PaymentException("Invalid webhook signature.");
        }

        String txRef;
        try {
            var node = objectMapper.readTree(rawPayload);
            txRef = node.path("tx_ref").asText();
        } catch (Exception e) {
            throw new PaymentException("Malformed webhook payload: " + e.getMessage());
        }

        // 2. Lookup payment — must exist
        Payment payment = paymentRepository.findByTxRef(txRef)
                .orElseThrow(() -> {
                    log.error("Webhook for unknown txRef: {}", txRef);
                    return new ResourceNotFoundException("Payment", "txRef", txRef);
                });

        // 3. Idempotency check — avoid double-processing
        if (payment.getStatus() == PaymentStatus.SUCCESS ||
            payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Webhook for already-processed payment txRef={}. Status={}. Skipping.",
                    txRef, payment.getStatus());
            return;
        }

        // 4. Verify with Chapa API (don't trust webhook alone)
        ChapaClient.ChapaVerifyResponse verify = chapaClient.verifyTransaction(txRef);

        if (verify != null && verify.isSuccessful() && providerVerificationMatches(payment, verify)) {
            String providerTxId = verify.getData() != null ? verify.getData().getReference() : "unknown";
            payment.markSuccess(providerTxId, rawPayload);
            paymentRepository.save(payment);

            // 5. Confirm the booking / queue entry
            confirmReferenceEntity(payment);

            // 6. Publish async event for notification
            eventProducer.publishPaymentSuccess(PaymentEvent.success(payment));
            log.info("Chapa payment SUCCESS: txRef={}, referenceId={}", txRef, payment.getReferenceId());
        } else {
            payment.markFailed("Verification failed or payment not successful", rawPayload);
            paymentRepository.save(payment);
            eventProducer.publishPaymentFailed(PaymentEvent.failed(payment));
            log.warn("Chapa payment FAILED: txRef={}", txRef);
        }
    }

    /**
     * Reconciles a single payment by checking its status with the provider's API.
     * Used by the reconciliation job for stuck PENDING payments.
     */
    @Transactional
    public void reconcile(Payment payment) {
        log.info("Reconciling payment txRef={} via provider {}", payment.getTxRef(), payment.getProvider());

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.debug("Payment txRef={} is not PENDING (status={}). Skipping reconciliation.",
                    payment.getTxRef(), payment.getStatus());
            return;
        }

        if (payment.getProvider() == PaymentProvider.CHAPA) {
            reconcileChapa(payment);
        } else {
            reconcileTelebirr(payment);
        }
    }

    private void reconcileChapa(Payment payment) {
        try {
            ChapaClient.ChapaVerifyResponse verify = chapaClient.verifyTransaction(payment.getTxRef());
            if (verify != null && verify.isSuccessful() && providerVerificationMatches(payment, verify)) {
                String providerTxId = verify.getData() != null ? verify.getData().getReference() : "unknown";
                payment.markSuccess(providerTxId, "RECONCILIATION_AUTO_SYNC");
                paymentRepository.save(payment);

                confirmReferenceEntity(payment);
                eventProducer.publishPaymentSuccess(PaymentEvent.success(payment));
                log.info("Reconciliation SUCCESS (Chapa): txRef={}", payment.getTxRef());
            } else {
                log.info("Reconciliation check (Chapa): txRef={} still not successful.", payment.getTxRef());
            }
        } catch (Exception e) {
            log.error("Error during Chapa reconciliation for txRef={}: {}", payment.getTxRef(), e.getMessage());
        }
    }

    private void reconcileTelebirr(Payment payment) {
        try {
            TelebirrClient.TelebirrVerifyResponse verify = telebirrClient.verifyTransaction(payment.getTxRef());
            if (verify != null && verify.isSuccessful() && providerVerificationMatches(payment, verify)) {
                payment.markSuccess(verify.getProviderTxId(), "RECONCILIATION_AUTO_SYNC");
                paymentRepository.save(payment);

                confirmReferenceEntity(payment);
                eventProducer.publishPaymentSuccess(PaymentEvent.success(payment));
                log.info("Reconciliation SUCCESS (Telebirr): txRef={}", payment.getTxRef());
            } else {
                log.info("Reconciliation check (Telebirr): txRef={} still not successful.", payment.getTxRef());
            }
        } catch (Exception e) {
            log.error("Error during Telebirr reconciliation for txRef={}: {}", payment.getTxRef(), e.getMessage());
        }
    }

    // ─── Telebirr Webhook ─────────────────────────────────────────────────

    @Transactional
    public void handleTelebirrWebhook(String rawPayload, String signature) {
        TelebirrClient.TelebirrWebhookPayload webhook = telebirrClient.parseAndVerifyWebhook(rawPayload, signature);

        if (webhook.getTxRef() == null || webhook.getTxRef().isBlank()) {
            throw new PaymentException("Telebirr webhook did not include a transaction reference.");
        }

        Payment payment = paymentRepository.findByTxRef(webhook.getTxRef())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "txRef", webhook.getTxRef()));

        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Telebirr webhook for already-processed payment txRef={}. Status={}. Skipping.",
                    payment.getTxRef(), payment.getStatus());
            return;
        }

        TelebirrClient.TelebirrVerifyResponse verify = telebirrClient.verifyTransaction(webhook.getTxRef());
        if (webhook.isSuccessful() && providerVerificationMatches(payment, verify)) {
            payment.markSuccess(
                    verify.getProviderTxId() != null ? verify.getProviderTxId() : webhook.getProviderTxId(),
                    rawPayload);
            paymentRepository.save(payment);

            confirmReferenceEntity(payment);
            eventProducer.publishPaymentSuccess(PaymentEvent.success(payment));
            log.info("Telebirr payment SUCCESS: txRef={}, referenceId={}", payment.getTxRef(), payment.getReferenceId());
        } else {
            payment.markFailed("Verification failed or payment not successful", rawPayload);
            paymentRepository.save(payment);
            eventProducer.publishPaymentFailed(PaymentEvent.failed(payment));
            log.warn("Telebirr payment FAILED: txRef={}", payment.getTxRef());
        }
    }

    // ─── Query ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentStatus(UUID requesterId, String requesterRole, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        if (!canAccessPayment(payment, requesterId, requesterRole)) {
            throw new AccessDeniedException("You are not allowed to access this payment.");
        }
        return toResponse(payment);
    }

    /**
     * Manually triggers reconciliation for a payment.
     * Authorized for the user who initiated the payment or an ADMIN.
     */
    @Transactional
    public PaymentResponse manualReconcile(UUID requesterId, String requesterRole, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));

        if (!canAccessPayment(payment, requesterId, requesterRole)) {
            throw new AccessDeniedException("You are not allowed to reconcile this payment.");
        }

        reconcile(payment);

        return toResponse(payment);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private void confirmReferenceEntity(Payment payment) {
        if (payment.getReferenceType() == PaymentReferenceType.APPOINTMENT) {
            try {
                bookingService.confirmAppointment(payment.getReferenceId());
            } catch (Exception e) {
                log.error("Failed to confirm appointment {} after payment success: {}",
                        payment.getReferenceId(), e.getMessage());
            }
            return;
        }
        log.info("Payment success recorded for queue entry {}. No queue status transition required.",
                payment.getReferenceId());
    }

    /**
     * HMAC-SHA256 signature verification for Chapa webhooks.
     * Chapa sends: X-Chapa-Signature: hmac_sha256(secret, raw_body)
     */
    private boolean verifyHmacSignature(String payload, String signature, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec =
                    new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] expected = bytesToHex(hash).toLowerCase().getBytes(StandardCharsets.UTF_8);
            byte[] provided = signature == null ? new byte[0] : signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            log.error("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }

    private ValidatedPaymentReference validateReference(UUID userId, InitiatePaymentRequest req) {
        return switch (req.getReferenceType()) {
            case APPOINTMENT -> validateAppointmentReference(userId, req);
            case QUEUE_ENTRY -> validateQueueReference(userId, req);
        };
    }

    private ValidatedPaymentReference validateAppointmentReference(UUID userId, InitiatePaymentRequest req) {
        Appointment appointment = appointmentRepository.findById(req.getReferenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", req.getReferenceId()));

        if (!appointment.getCustomer().getId().equals(userId)) {
            throw new AccessDeniedException("You can only pay for your own appointments.");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BusinessException("Only pending appointments can be paid.");
        }
        verifyRequestedAmount(req.getAmount(), appointment.getPriceCharged());
        return new ValidatedPaymentReference(appointment.getPriceCharged());
    }

    private ValidatedPaymentReference validateQueueReference(UUID userId, InitiatePaymentRequest req) {
        QueueEntry entry = queueEntryRepository.findById(req.getReferenceId())
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", req.getReferenceId()));

        if (!entry.getCustomer().getId().equals(userId)) {
            throw new AccessDeniedException("You can only pay for your own queue entries.");
        }
        if (entry.getStatus() == QueueStatus.COMPLETED ||
            entry.getStatus() == QueueStatus.CANCELLED ||
            entry.getStatus() == QueueStatus.SKIPPED) {
            throw new BusinessException("This queue entry is no longer payable.");
        }

        BigDecimal expectedAmount = entry.getBarber().getServiceAssignments().stream()
                .filter(BarberServiceAssignment::isActive)
                .filter(assignment -> assignment.getService().getId().equals(entry.getService().getId()))
                .map(assignment -> assignment.getCustomPrice() != null
                        ? assignment.getCustomPrice()
                        : entry.getService().getBasePrice())
                .findFirst()
                .orElse(entry.getService().getBasePrice());

        verifyRequestedAmount(req.getAmount(), expectedAmount);
        return new ValidatedPaymentReference(expectedAmount);
    }

    private void verifyRequestedAmount(BigDecimal requestedAmount, BigDecimal expectedAmount) {
        if (requestedAmount == null || expectedAmount == null ||
            requestedAmount.setScale(2, java.math.RoundingMode.HALF_UP)
                    .compareTo(expectedAmount.setScale(2, java.math.RoundingMode.HALF_UP)) != 0) {
            throw new PaymentException("Requested payment amount does not match the authoritative price.");
        }
    }

    private boolean providerVerificationMatches(Payment payment, ChapaClient.ChapaVerifyResponse verify) {
        if (verify.getData() == null) {
            return false;
        }

        boolean txRefMatches = payment.getTxRef().equals(verify.getData().getTxRef());
        boolean amountMatches = verify.getData().getAmount() != null &&
                payment.getAmount().setScale(2, java.math.RoundingMode.HALF_UP)
                        .compareTo(verify.getData().getAmount().setScale(2, java.math.RoundingMode.HALF_UP)) == 0;
        boolean currencyMatches = payment.getCurrency().equalsIgnoreCase(verify.getData().getCurrency());

        if (!txRefMatches || !amountMatches || !currencyMatches) {
            log.error("Chapa verification mismatch for txRef={}: txRefMatches={}, amountMatches={}, currencyMatches={}",
                    payment.getTxRef(), txRefMatches, amountMatches, currencyMatches);
        }

        return txRefMatches && amountMatches && currencyMatches;
    }

    private boolean providerVerificationMatches(Payment payment, TelebirrClient.TelebirrVerifyResponse verify) {
        boolean statusMatches = verify.isSuccessful();
        boolean txRefMatches = payment.getTxRef().equals(verify.getTxRef());
        boolean amountMatches = verify.getAmount() != null &&
                payment.getAmount().setScale(2, java.math.RoundingMode.HALF_UP)
                        .compareTo(verify.getAmount().setScale(2, java.math.RoundingMode.HALF_UP)) == 0;
        boolean currencyMatches = verify.getCurrency() != null &&
                payment.getCurrency().equalsIgnoreCase(verify.getCurrency());

        if (!statusMatches || !txRefMatches || !amountMatches || !currencyMatches) {
            log.error("Telebirr verification mismatch for txRef={}: statusMatches={}, txRefMatches={}, amountMatches={}, currencyMatches={}",
                    payment.getTxRef(), statusMatches, txRefMatches, amountMatches, currencyMatches);
        }

        return statusMatches && txRefMatches && amountMatches && currencyMatches;
    }

    private boolean canAccessPayment(Payment payment, UUID requesterId, String requesterRole) {
        if ("ADMIN".equalsIgnoreCase(requesterRole)) {
            return true;
        }
        return payment.getUser().getId().equals(requesterId);
    }

    private record ValidatedPaymentReference(BigDecimal amount) {}

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .txRef(p.getTxRef())
                .provider(p.getProvider())
                .status(p.getStatus())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .checkoutUrl(p.getCheckoutUrl())
                .referenceId(p.getReferenceId())
                .referenceType(p.getReferenceType())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
