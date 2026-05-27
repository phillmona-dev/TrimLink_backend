package com.trimlink.module.notification.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the SMS service.
 * Checks if the API credentials are provided.
 */
@Slf4j
@Component
public class SmsHealthIndicator implements HealthIndicator {

    @Value("${trimlink.notification.sms.api-key:}")
    private String apiKey;

    @Value("${trimlink.notification.sms.provider}")
    private String provider;

    @Override
    public Health health() {
        if (apiKey == null || apiKey.isBlank()) {
            return Health.down()
                    .withDetail("reason", "SMS API key is missing")
                    .withDetail("provider", provider)
                    .build();
        }

        return Health.up()
                .withDetail("provider", provider)
                .build();
    }
}
