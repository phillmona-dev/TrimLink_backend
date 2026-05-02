package com.trimlink.module.booking.entity;

/**
 * Finite state machine for appointment lifecycle.
 *
 * PENDING   → user created booking, awaiting confirmation
 * CONFIRMED → barber/shop confirmed the slot
 * COMPLETED → service was delivered
 * CANCELLED → cancelled by user or barber
 * NO_SHOW   → customer did not arrive
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    REJECTED,
    RESCHEDULE_REQUESTED
}
