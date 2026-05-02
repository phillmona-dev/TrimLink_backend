package com.trimlink.module.payment.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trimlink.common.exception.PaymentException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Telebirr client with configurable signing and webhook verification.
 *
 * The exact public merchant documentation is not broadly accessible, so this client keeps
 * provider-specific request fields isolated and configurable while still enforcing:
 * - signed initiation requests
 * - explicit provider-side verification before marking success
 * - signature-checked webhook parsing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelebirrClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${trimlink.payment.telebirr.base-url}")
    private String baseUrl;

    @Value("${trimlink.payment.telebirr.app-id}")
    private String appId;

    @Value("${trimlink.payment.telebirr.app-key}")
    private String appKey;

    @Value("${trimlink.payment.telebirr.public-key}")
    private String publicKey;

    @Value("${trimlink.payment.telebirr.notify-url}")
    private String notifyUrl;

    @Value("${trimlink.payment.telebirr.return-url:${trimlink.app.base-url}/payments/return}")
    private String returnUrl;

    @Value("${trimlink.payment.telebirr.short-code}")
    private String shortCode;

    @Value("${trimlink.payment.telebirr.initiate-path:/merchant/v1/payment/preorder}")
    private String initiatePath;

    @Value("${trimlink.payment.telebirr.verify-path:/merchant/v1/payment/query}")
    private String verifyPath;

    @Value("${trimlink.payment.telebirr.signature-algorithm:RSA}")
    private String signatureAlgorithm;

    @Value("${trimlink.payment.telebirr.timeout-seconds:900}")
    private int timeoutSeconds;

    @CircuitBreaker(name = "telebirr", fallbackMethod = "initiateFallback")
    @Retry(name = "telebirr")
    public TelebirrInitResponse initiatePayment(TelebirrInitRequest request) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("merch_code", shortCode);
        bizContent.put("out_trade_no", request.getTxRef());
        bizContent.put("trade_type", "Checkout");
        bizContent.put("total_amount", request.getAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        bizContent.put("trans_currency", "ETB");
        bizContent.put("timeout_express", timeoutSeconds);
        bizContent.put("title", request.getTitle());
        bizContent.put("subject", request.getDescription());
        bizContent.put("notify_url", notifyUrl);
        bizContent.put("return_url", returnUrl);
        bizContent.put("payer_phone", request.getPhoneNumber());

        Map<String, Object> body = signedEnvelope(bizContent);

        try {
            JsonNode response = webClient()
                    .post()
                    .uri(initiatePath)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String checkoutUrl = firstText(response,
                    "/checkout_url",
                    "/pay_url",
                    "/to_pay_url",
                    "/data/checkout_url",
                    "/data/pay_url",
                    "/data/to_pay_url",
                    "/biz_content/pay_url",
                    "/biz_content/to_pay_url");

            if (!StringUtils.hasText(checkoutUrl)) {
                throw new PaymentException("Telebirr did not return a checkout URL.");
            }

            return TelebirrInitResponse.builder()
                    .checkoutUrl(checkoutUrl)
                    .providerResponse(response)
                    .build();
        } catch (WebClientResponseException ex) {
            log.error("Telebirr initiation failed: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PaymentException("Telebirr payment initiation failed: " + ex.getMessage(), ex);
        }
    }

    @CircuitBreaker(name = "telebirr", fallbackMethod = "verifyFallback")
    @Retry(name = "telebirr")
    public TelebirrVerifyResponse verifyTransaction(String txRef) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("merch_code", shortCode);
        bizContent.put("out_trade_no", txRef);

        try {
            JsonNode response = webClient()
                    .post()
                    .uri(verifyPath)
                    .bodyValue(signedEnvelope(bizContent))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode payload = unwrapPayload(response);

            return TelebirrVerifyResponse.builder()
                    .successful(isSuccessfulResponse(response, payload))
                    .providerTxId(firstText(payload, "/trade_no", "/transaction_id", "/transactionId", "/reference"))
                    .txRef(firstText(payload, "/out_trade_no", "/merchant_trade_no", "/tx_ref", "/outTradeNo"))
                    .amount(readDecimal(payload, "/total_amount", "/amount"))
                    .currency(firstText(payload, "/trans_currency", "/currency"))
                    .providerResponse(response)
                    .build();
        } catch (WebClientResponseException ex) {
            log.error("Telebirr verification failed: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PaymentException("Telebirr verification failed: " + ex.getMessage(), ex);
        }
    }

    public TelebirrWebhookPayload parseAndVerifyWebhook(String rawPayload, String headerSignature) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode payload = unwrapPayload(root);
            String signedContent = extractSignedContent(root, payload);
            String signature = StringUtils.hasText(headerSignature)
                    ? headerSignature.trim()
                    : firstText(root, "/sign", "/signature");

            if (!StringUtils.hasText(signature) || !verifySignature(signedContent, signature)) {
                throw new PaymentException("Invalid Telebirr webhook signature.");
            }

            return TelebirrWebhookPayload.builder()
                    .txRef(firstText(payload, "/out_trade_no", "/merchant_trade_no", "/tx_ref", "/outTradeNo"))
                    .providerTxId(firstText(payload, "/trade_no", "/transaction_id", "/transactionId", "/reference"))
                    .status(firstText(payload, "/trade_status", "/status", "/tradeStatus"))
                    .amount(readDecimal(payload, "/total_amount", "/amount"))
                    .currency(firstText(payload, "/trans_currency", "/currency"))
                    .rawPayload(rawPayload)
                    .build();
        } catch (JsonProcessingException ex) {
            throw new PaymentException("Malformed Telebirr webhook payload: " + ex.getMessage(), ex);
        }
    }

    public TelebirrInitResponse initiateFallback(TelebirrInitRequest request, Throwable throwable) {
        log.error("Telebirr circuit breaker open. txRef={}", request.getTxRef(), throwable);
        throw new PaymentException("Telebirr payment service is temporarily unavailable. Please try again shortly.");
    }

    public TelebirrVerifyResponse verifyFallback(String txRef, Throwable throwable) {
        log.error("Telebirr verify circuit breaker open. txRef={}", txRef, throwable);
        throw new PaymentException("Unable to verify payment with Telebirr. Please contact support.");
    }

    private WebClient webClient() {
        if (!StringUtils.hasText(baseUrl)) {
            throw new PaymentException("Telebirr base URL is not configured.");
        }
        if (!StringUtils.hasText(appId)) {
            throw new PaymentException("Telebirr app id is not configured.");
        }
        if (!StringUtils.hasText(appKey)) {
            throw new PaymentException("Telebirr app key is not configured.");
        }
        return webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private Map<String, Object> signedEnvelope(Map<String, Object> bizContent) {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String content = canonicalJson(bizContent);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("appid", appId);
        request.put("timestamp", timestamp);
        request.put("nonce_str", nonce);
        request.put("sign_type", effectiveSignatureAlgorithm());
        request.put("sign", sign(content));
        request.put("biz_content", bizContent);
        return request;
    }

    private JsonNode unwrapPayload(JsonNode root) {
        if (root == null || root.isNull()) {
            return objectMapper.nullNode();
        }

        if (root.hasNonNull("biz_content")) {
            return parseNode(root.get("biz_content"));
        }
        if (root.hasNonNull("data")) {
            return parseNode(root.get("data"));
        }
        return root;
    }

    private JsonNode parseNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isTextual()) {
            try {
                return objectMapper.readTree(node.asText());
            } catch (JsonProcessingException ex) {
                throw new PaymentException("Unable to parse Telebirr nested payload.", ex);
            }
        }
        return node;
    }

    private String extractSignedContent(JsonNode root, JsonNode payload) {
        if (root.hasNonNull("biz_content")) {
            JsonNode content = root.get("biz_content");
            return content.isTextual() ? content.asText() : canonicalJson(content);
        }
        if (root.hasNonNull("data")) {
            JsonNode content = root.get("data");
            return content.isTextual() ? content.asText() : canonicalJson(content);
        }
        return canonicalJson(payload);
    }

    private boolean isSuccessfulResponse(JsonNode response, JsonNode payload) {
        String topLevelStatus = firstText(response, "/code", "/status", "/result_code", "/trade_status");
        String payloadStatus = firstText(payload, "/trade_status", "/status", "/result");

        return matchesSuccess(topLevelStatus) || matchesSuccess(payloadStatus);
    }

    private boolean matchesSuccess(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return normalized.equals("SUCCESS")
                || normalized.equals("COMPLETED")
                || normalized.equals("FINISHED")
                || normalized.equals("PAY_SUCCESS")
                || normalized.equals("200")
                || normalized.equals("0");
    }

    private BigDecimal readDecimal(JsonNode node, String... pointers) {
        for (String pointer : pointers) {
            JsonNode value = node.at(pointer);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                try {
                    return new BigDecimal(value.asText());
                } catch (NumberFormatException ignored) {
                    log.warn("Unable to parse decimal value '{}' from Telebirr payload pointer {}", value.asText(), pointer);
                }
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... pointers) {
        for (String pointer : pointers) {
            JsonNode value = node.at(pointer);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private String sign(String content) {
        return switch (effectiveSignatureAlgorithm()) {
            case "HMAC-SHA256" -> hmacSign(content);
            default -> rsaSign(content);
        };
    }

    private boolean verifySignature(String content, String signature) {
        return switch (effectiveSignatureAlgorithm()) {
            case "HMAC-SHA256" -> MessageDigest.isEqual(
                    hmacSign(content).getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8));
            default -> rsaVerify(content, signature);
        };
    }

    private String effectiveSignatureAlgorithm() {
        String normalized = signatureAlgorithm == null ? "" : signatureAlgorithm.trim().toUpperCase();
        if ("HMAC".equals(normalized) || "HMAC_SHA256".equals(normalized) || "HMAC-SHA256".equals(normalized)) {
            return "HMAC-SHA256";
        }
        return "SHA256WITHRSA";
    }

    private String hmacSign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return bytesToHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new PaymentException("Unable to sign Telebirr request with HMAC.", ex);
        }
    }

    private String rsaSign(String content) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(readPrivateKey(appKey));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new PaymentException("Unable to sign Telebirr request.", ex);
        }
    }

    private boolean rsaVerify(String content, String signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(readPublicKey(publicKey));
            verifier.update(content.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception ex) {
            log.error("Telebirr RSA verification failed: {}", ex.getMessage());
            return false;
        }
    }

    private PrivateKey readPrivateKey(String rawKey) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(sanitizePem(rawKey));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private PublicKey readPublicKey(String rawKey) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(sanitizePem(rawKey));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    private String sanitizePem(String key) {
        return key.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }

    private String canonicalJson(Object value) {
        try {
            if (value instanceof JsonNode node) {
                return objectMapper.writeValueAsString(node);
            }
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new PaymentException("Unable to build canonical Telebirr payload.", ex);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    @Getter
    @Builder
    public static class TelebirrInitRequest {
        private final String txRef;
        private final BigDecimal amount;
        private final String phoneNumber;
        private final String title;
        private final String description;
    }

    @Getter
    @Builder
    public static class TelebirrInitResponse {
        private final String checkoutUrl;
        private final JsonNode providerResponse;
    }

    @Getter
    @Builder
    public static class TelebirrVerifyResponse {
        private final boolean successful;
        private final String providerTxId;
        private final String txRef;
        private final BigDecimal amount;
        private final String currency;
        private final JsonNode providerResponse;
    }

    @Getter
    @Builder
    public static class TelebirrWebhookPayload {
        private final String txRef;
        private final String providerTxId;
        private final String status;
        private final BigDecimal amount;
        private final String currency;
        private final String rawPayload;

        public boolean isSuccessful() {
            if (!StringUtils.hasText(status)) {
                return false;
            }
            String normalized = status.trim().toUpperCase();
            return normalized.equals("SUCCESS")
                    || normalized.equals("COMPLETED")
                    || normalized.equals("FINISHED")
                    || normalized.equals("PAY_SUCCESS");
        }
    }
}
