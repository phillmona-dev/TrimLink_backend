package com.trimlink.module.queue.entity;

import com.trimlink.common.audit.BaseEntity;
import com.trimlink.module.service.entity.Service;
import com.trimlink.module.shop.entity.StaffShop;
import com.trimlink.module.user.entity.StaffProfile;
import com.trimlink.module.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a walk-in customer in the per-staff FIFO queue.
 *
 * Queue ordering is determined by (staffId, joinedAt ASC).
 * Position numbers are virtual — computed at query time — so there's no
 * "position" column that needs to be updated when someone leaves.
 *
 * Offline sync: clients attach a clientTimestamp (their local clock)
 * so the server can reconcile late-arriving join requests correctly.
 */
@Entity
@Table(name = "queue_entries", indexes = {
        @Index(name = "idx_queue_staff_status", columnList = "staff_profile_id, status"),
        @Index(name = "idx_queue_shop_status",   columnList = "shop_id, status"),
        @Index(name = "idx_queue_joined_at",     columnList = "joined_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_profile_id", nullable = false)
    private StaffProfile staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private StaffShop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    /**
     * The moment the customer physically joined (server time).
     * For FIFO ordering. Indexed.
     */
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    /**
     * Client-reported timestamp used for offline sync reconciliation.
     * When a customer joins while offline and syncs later,
     * their position is determined by this timestamp, not server arrival time.
     */
    @Column(name = "client_timestamp")
    private LocalDateTime clientTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private QueueStatus status = QueueStatus.WAITING;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    @Column(name = "service_started_at")
    private LocalDateTime serviceStartedAt;

    @Column(name = "service_ended_at")
    private LocalDateTime serviceEndedAt;

    @Column(name = "notes", length = 300)
    private String notes;

    // ─── FSM transitions ───────────────────────────────────────────────────

    public void call() {
        this.status = QueueStatus.CALLED;
        this.calledAt = LocalDateTime.now();
    }

    public void startService() {
        this.status = QueueStatus.IN_SERVICE;
        this.serviceStartedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = QueueStatus.COMPLETED;
        this.serviceEndedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = QueueStatus.CANCELLED;
    }

    public void skip() {
        this.status = QueueStatus.SKIPPED;
    }
}
