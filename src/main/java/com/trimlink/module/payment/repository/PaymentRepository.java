package com.trimlink.module.payment.repository;

import com.trimlink.module.payment.entity.Payment;
import com.trimlink.module.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Find by our transaction reference — primary idempotency lookup key.
     */
    Optional<Payment> findByTxRef(String txRef);

    /**
     * Check existence before creating to prevent duplicate payment records.
     */
    boolean existsByReferenceIdAndReferenceTypeAndStatusIn(
            UUID referenceId,
            com.trimlink.module.payment.entity.PaymentReferenceType referenceType,
            java.util.List<PaymentStatus> statuses);

    Optional<Payment> findByReferenceId(UUID referenceId);

    /**
     * Find pending payments that were created before a specific time.
     * Used for reconciliation jobs to pick up "stuck" payments.
     */
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Payment p WHERE p.status = 'PENDING' AND p.createdAt < :cutoff")
    java.util.List<Payment> findPendingOlderThan(java.time.LocalDateTime cutoff);
}
