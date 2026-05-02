package com.trimlink.module.payment.controller;

import com.trimlink.module.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook receiver for Chapa and Telebirr payment callbacks.
 *
 * ⚠️  These endpoints are PUBLIC (no JWT required) — they are called by
 *     external payment providers, not by our clients.
 *
 * Security model:
 *  - Chapa:   HMAC-SHA256 signature in X-Chapa-Signature header
 *  - Telebirr: signed callback payload verified against provider public key
 *
 * Idempotency:
 *  - PaymentService checks payment.status before processing
 *  - Duplicate webhooks for the same tx_ref are silently accepted (200 OK)
 *    to prevent provider retry storms
 *
 * Both endpoints always return 200 OK to the provider (even on soft errors),
 * then handle failures internally — this prevents endless retries from the gateway.
 */
@Slf4j
@Tag(name = "Webhooks", description = "Payment provider webhook receivers")
@RestController
@RequestMapping("/webhooks/payment")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    /**
     * POST /webhooks/payment/chapa
     *
     * Chapa sends a POST with the raw JSON body and
     * X-Chapa-Signature: <HMAC-SHA256 of body using webhook secret>
     */
    @Operation(summary = "Chapa payment webhook callback")
    @PostMapping("/chapa")
    public ResponseEntity<String> handleChapaWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Chapa-Signature", required = false,
                           defaultValue = "") String signature) {

        log.info("Chapa webhook received. PayloadLength={}", rawPayload.length());
        try {
            paymentService.handleChapaWebhook(rawPayload, signature);
        } catch (Exception e) {
            // Log failure but return 200 to prevent Chapa from retrying indefinitely.
            // Internal alerts / dead-letter queue handle the failure asynchronously.
            log.error("Chapa webhook processing error: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok("OK");
    }

    /**
     * POST /webhooks/payment/telebirr
     *
     * Telebirr sends a signed callback payload.
     */
    @Operation(summary = "Telebirr payment webhook callback")
    @PostMapping("/telebirr")
    public ResponseEntity<String> handleTelebirrWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Telebirr-Signature", required = false, defaultValue = "") String signature) {

        log.info("Telebirr webhook received. PayloadLength={}", rawPayload.length());
        try {
            paymentService.handleTelebirrWebhook(rawPayload, signature);
        } catch (Exception e) {
            log.error("Telebirr webhook processing error: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok("OK");
    }
}
