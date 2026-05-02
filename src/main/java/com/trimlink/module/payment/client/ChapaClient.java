package com.trimlink.module.payment.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trimlink.common.exception.PaymentException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * HTTP client for the Chapa payment gateway.
 * Wrapped with Circuit Breaker + Retry (Resilience4j) for fault tolerance.
 *
 * Chapa API: https://developer.chapa.co/
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapaClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${trimlink.payment.chapa.base-url}")
    private String baseUrl;

    @Value("${trimlink.payment.chapa.secret-key}")
    private String secretKey;

    @Value("${trimlink.payment.chapa.callback-url:}")
    private String callbackUrl;

    @Value("${trimlink.payment.chapa.return-url:}")
    private String returnUrl;

    // ─── Initiate Payment ─────────────────────────────────────────────────

    /**
     * Initiates a Chapa checkout session.
     * Returns the checkout URL to redirect the user.
     */
    @CircuitBreaker(name = "chapa", fallbackMethod = "initiateFallback")
    @Retry(name = "chapa")
    public ChapaInitResponse initiatePayment(ChapaInitRequest request) {
        log.info("Initiating Chapa payment: txRef={}, amount={}", request.getTxRef(), request.getAmount());
        try {
            return webClient()
                    .post()
                    .uri("/transaction/initialize")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChapaInitResponse.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Chapa initiation failed: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PaymentException("Chapa payment initiation failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Verifies a transaction with Chapa after webhook is received.
     * This is the second-factor check before marking a payment SUCCESS.
     */
    @CircuitBreaker(name = "chapa", fallbackMethod = "verifyFallback")
    @Retry(name = "chapa")
    public ChapaVerifyResponse verifyTransaction(String txRef) {
        log.info("Verifying Chapa transaction: txRef={}", txRef);
        try {
            return webClient()
                    .get()
                    .uri("/transaction/verify/{txRef}", txRef)
                    .retrieve()
                    .bodyToMono(ChapaVerifyResponse.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Chapa verification failed: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PaymentException("Chapa verification failed: " + ex.getMessage(), ex);
        }
    }

    // ─── Fallbacks ────────────────────────────────────────────────────────

    public ChapaInitResponse initiateFallback(ChapaInitRequest req, Throwable t) {
        log.error("Chapa circuit breaker open. txRef={}", req.getTxRef());
        throw new PaymentException("Chapa payment service is temporarily unavailable. Please try again shortly.");
    }

    public ChapaVerifyResponse verifyFallback(String txRef, Throwable t) {
        log.error("Chapa verify circuit breaker open. txRef={}", txRef);
        throw new PaymentException("Unable to verify payment with Chapa. Please contact support.");
    }

    // ─── WebClient ────────────────────────────────────────────────────────

    private WebClient webClient() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new PaymentException("Chapa base URL is not configured.");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new PaymentException("Chapa secret key is not configured.");
        }
        return webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    // ─── Request / Response DTOs ──────────────────────────────────────────

    @Data
    public static class ChapaInitRequest {
        private String amount;
        private String currency = "ETB";
        @JsonProperty("tx_ref")   private String txRef;
        @JsonProperty("email")    private String email;
        @JsonProperty("first_name") private String firstName;
        @JsonProperty("last_name")  private String lastName;
        @JsonProperty("phone_number") private String phoneNumber;
        @JsonProperty("callback_url") private String callbackUrl;
        @JsonProperty("return_url")   private String returnUrl;
        private Map<String, String> customization;
    }

    @Data
    public static class ChapaInitResponse {
        private String message;
        private String status;
        private DataPayload data;

        @Data
        public static class DataPayload {
            @JsonProperty("checkout_url") private String checkoutUrl;
        }
    }

    @Data
    public static class ChapaVerifyResponse {
        private String message;
        private String status;
        private VerifyData data;

        @Data
        public static class VerifyData {
            private String status;            // "success" | "failed"
            @JsonProperty("tx_ref")          private String txRef;
            @JsonProperty("amount")          private BigDecimal amount;
            @JsonProperty("currency")        private String currency;
            @JsonProperty("reference")       private String reference;
        }

        public boolean isSuccessful() {
            return "success".equalsIgnoreCase(status) &&
                   data != null && "success".equalsIgnoreCase(data.getStatus());
        }
    }
}
