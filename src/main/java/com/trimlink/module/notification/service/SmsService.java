package com.trimlink.module.notification.service;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * SMS service using AfroMessage (Ethiopian SMS provider).
 * AfroMessage API: https://afromessage.com/developers
 *
 * For low-bandwidth environments:
 * - Messages are kept short (≤ 160 chars where possible)
 * - WebClient is used with retries for high reliability in production
 */
@Slf4j
@Service
public class SmsService {

    private final WebClient webClient;
    private final String apiKey;
    private final String senderId;
    private final String identifierId;
    private final String apiUrl;

    public SmsService(
            WebClient.Builder webClientBuilder,
            @Value("${trimlink.notification.sms.api-url}") String apiUrl,
            @Value("${trimlink.notification.sms.api-key}") String apiKey,
            @Value("${trimlink.notification.sms.sender-id}") String senderId,
            @Value("${trimlink.notification.sms.identifier-id:}") String identifierId) {
        this.apiUrl   = apiUrl;
        this.apiKey   = apiKey;
        this.senderId = senderId;
        this.identifierId = identifierId;
        this.webClient = webClientBuilder.baseUrl(apiUrl).build();
    }

    /**
     * Send an SMS message to a phone number.
     * Uses Resilience4j @Retry to handle transient network issues.
     *
     * @param to      Phone number in E.164 format (+251...)
     * @param message SMS body (≤ 160 chars recommended for single-part SMS)
     */
    @Retry(name = "sms")
    public void send(String to, String message) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("SMS not sent (no API key configured). To={}, Message={}", to, message);
            return;
        }

        log.info("Sending SMS to={}, length={}", to, message.length());

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("from", identifierId)
                            .queryParam("sender", senderId)
                            .queryParam("to", to)
                            .queryParam("message", truncate(message, 160))
                            .build())
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // Blocking call to allow @Retry to catch exceptions

            log.info("SMS sent to {}. Response: {}", to, response);
        } catch (Exception e) {
            log.error("SMS attempt failed for {}: {}", to, e.getMessage());
            throw e; // Rethrow to trigger Resilience4j retry
        }
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
