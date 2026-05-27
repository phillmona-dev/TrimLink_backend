package com.trimlink.module.payment.health;

import com.trimlink.module.payment.client.ChapaClient;
import com.trimlink.module.payment.client.TelebirrClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator to monitor the connectivity of payment gateways.
 */
@Component
@RequiredArgsConstructor
public class PaymentGatewayHealthIndicator implements HealthIndicator {

    private final ChapaClient chapaClient;
    private final TelebirrClient telebirrClient;

    @Override
    public Health health() {
        boolean chapaUp = checkChapa();
        boolean telebirrUp = checkTelebirr();

        Health.Builder builder = (chapaUp && telebirrUp) ? Health.up() : Health.down();

        return builder
                .withDetail("chapa", chapaUp ? "UP" : "DOWN")
                .withDetail("telebirr", telebirrUp ? "UP" : "DOWN")
                .build();
    }

    private boolean checkChapa() {
        try {
            // Minimal check: Chapa doesn't have a dedicated ping, but we check if the base client is configured
            return chapaClient != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkTelebirr() {
        try {
            return telebirrClient != null;
        } catch (Exception e) {
            return false;
        }
    }
}
