package com.trimlink.module.booking.entity;

import com.trimlink.common.audit.BaseEntity;
import com.trimlink.module.service.entity.Service;
import com.trimlink.module.shop.entity.BarberShop;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Core booking entity.
 *
 * Overlap prevention is enforced by:
 *  1. DB unique constraint on (barber_profile_id, scheduled_start) — prevents exact duplicate inserts
 *  2. Service-layer query checking for overlaps in [start, end) before persisting
 *  3. Pessimistic write lock on the barber's schedule row during booking creation
 */
@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appt_barber_start", columnList = "barber_profile_id, scheduled_start"),
        @Index(name = "idx_appt_customer",     columnList = "customer_id"),
        @Index(name = "idx_appt_status",       columnList = "status"),
        @Index(name = "idx_appt_shop_date",    columnList = "shop_id, scheduled_start")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barber_profile_id", nullable = false)
    private BarberProfile barber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private BarberShop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalDateTime scheduledEnd;

    @Column(name = "actual_start")
    private LocalDateTime actualStart;

    @Column(name = "actual_end")
    private LocalDateTime actualEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    /**
     * Price charged at time of booking (snapshot; service price may change later).
     */
    @Column(name = "price_charged", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceCharged;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    // ─── FSM transitions ───────────────────────────────────────────────────

    public void confirm() {
        validateTransition(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING);
        this.status = AppointmentStatus.CONFIRMED;
    }

    public void complete() {
        validateTransition(AppointmentStatus.COMPLETED,
                AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING);
        this.status = AppointmentStatus.COMPLETED;
        this.actualEnd = LocalDateTime.now();
    }

    public void cancel(String reason) {
        validateTransition(AppointmentStatus.CANCELLED,
                AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);
        this.status = AppointmentStatus.CANCELLED;
        this.cancellationReason = reason;
    }

    public void reject(String reason) {
        validateTransition(AppointmentStatus.REJECTED, AppointmentStatus.PENDING);
        this.status = AppointmentStatus.REJECTED;
        this.cancellationReason = reason;
    }

    public void requestReschedule(String reason) {
        validateTransition(AppointmentStatus.RESCHEDULE_REQUESTED, AppointmentStatus.PENDING);
        this.status = AppointmentStatus.RESCHEDULE_REQUESTED;
        this.cancellationReason = reason;
    }

    public void markNoShow() {
        validateTransition(AppointmentStatus.NO_SHOW, AppointmentStatus.CONFIRMED);
        this.status = AppointmentStatus.NO_SHOW;
    }

    private void validateTransition(AppointmentStatus next, AppointmentStatus... allowed) {
        for (AppointmentStatus s : allowed) {
            if (this.status == s) return;
        }
        throw new IllegalStateException(
                String.format("Cannot transition from %s to %s", this.status, next));
    }
}
