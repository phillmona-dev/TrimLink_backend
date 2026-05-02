package com.trimlink.config;

import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class ProductionReadinessValidator {

    private static final List<String> DISALLOWED_PLACEHOLDERS = List.of(
            "CHASECK-xxxx",
            "chapa_wh_secret",
            "app_id",
            "app_key",
            "telebirr_pub_key"
    );

    private final Environment environment;

    @Value("${trimlink.security.jwt.secret:}")
    private String jwtSecret;

    @Value("${trimlink.security.cors.allowed-origin-patterns:}")
    private String corsAllowedOriginPatterns;

    @Value("${trimlink.payment.chapa.secret-key:}")
    private String chapaSecretKey;

    @Value("${trimlink.payment.chapa.webhook-secret:}")
    private String chapaWebhookSecret;

    @Value("${trimlink.payment.telebirr.app-id:}")
    private String telebirrAppId;

    @Value("${trimlink.payment.telebirr.app-key:}")
    private String telebirrAppKey;

    @Value("${trimlink.payment.telebirr.public-key:}")
    private String telebirrPublicKey;

    @Value("${trimlink.notification.push.enabled:false}")
    private boolean pushEnabled;

    @Value("${trimlink.notification.push.firebase-credentials-path:}")
    private String firebaseCredentialsPath;

    ProductionReadinessValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        boolean testProfile = Arrays.stream(environment.getActiveProfiles()).anyMatch("test"::equalsIgnoreCase);
        if (testProfile) {
            return;
        }

        validateJwtSecret();

        boolean prodProfile = Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
        if (prodProfile) {
            validateProductionCors();
            validateNoPlaceholder("trimlink.payment.chapa.secret-key", chapaSecretKey);
            validateNoPlaceholder("trimlink.payment.chapa.webhook-secret", chapaWebhookSecret);
            validateNoPlaceholder("trimlink.payment.telebirr.app-id", telebirrAppId);
            validateNoPlaceholder("trimlink.payment.telebirr.app-key", telebirrAppKey);
            validateNoPlaceholder("trimlink.payment.telebirr.public-key", telebirrPublicKey);
            validatePushConfiguration();
        }
    }

    private void validateJwtSecret() {
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("JWT secret is required. Set JWT_SECRET to a base64-encoded 256-bit secret.");
        }

        try {
            byte[] decoded = Decoders.BASE64.decode(jwtSecret);
            if (decoded.length < 32) {
                throw new IllegalStateException("JWT secret must decode to at least 32 bytes.");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT secret must be base64-encoded.", ex);
        }
    }

    private void validateProductionCors() {
        if (!StringUtils.hasText(corsAllowedOriginPatterns)) {
            throw new IllegalStateException("CORS allowed origins must be configured for production.");
        }

        boolean containsWildcard = Arrays.stream(corsAllowedOriginPatterns.split(","))
                .map(String::trim)
                .anyMatch(origin -> origin.equals("*") || origin.contains("*"));
        if (containsWildcard) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed in production.");
        }
    }

    private void validateNoPlaceholder(String propertyName, String value) {
        if (!StringUtils.hasText(value) || DISALLOWED_PLACEHOLDERS.contains(value.trim())) {
            throw new IllegalStateException(propertyName + " must be configured with a real secret in production.");
        }
        log.info("Validated production property {}", propertyName);
    }

    private void validatePushConfiguration() {
        if (pushEnabled && !StringUtils.hasText(firebaseCredentialsPath)) {
            throw new IllegalStateException(
                    "trimlink.notification.push.firebase-credentials-path must be configured when push notifications are enabled in production.");
        }
    }
}
