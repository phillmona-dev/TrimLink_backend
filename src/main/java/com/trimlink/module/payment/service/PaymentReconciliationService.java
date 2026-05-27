package com.trimlink.module.payment.service;

import com.trimlink.module.payment.entity.Payment;
import com.trimlink.module.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Background service to reconcile stuck payments.
 * 
 * In production, webhooks might fail due to network issues or server downtime.
 * This job ensures that any payment that was successful on the provider side
 * but stuck as PENDING in our system is eventually synchronized.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    /**
     * Runs every 10 minutes.
     * Picks up PENDING payments that are at least 15 minutes old.
     */
    @Scheduled(fixedRateString = "${trimlink.payment.reconciliation.fixed-rate:600000}")
    public void reconcileStuckPayments() {
        log.info("Starting payment reconciliation job...");

        // Threshold: 15 minutes ago
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        
        List<Payment> stuckPayments = paymentRepository.findPendingOlderThan(cutoff);
        
        if (stuckPayments.isEmpty()) {
            log.info("No stuck payments found for reconciliation.");
            return;
        }

        log.info("Found {} stuck payments to reconcile.", stuckPayments.size());

        for (Payment payment : stuckPayments) {
            try {
                paymentService.reconcile(payment);
            } catch (Exception e) {
                log.error("Failed to reconcile payment txRef={}: {}", payment.getTxRef(), e.getMessage());
            }
        }

        log.info("Payment reconciliation job completed.");
    }
}
