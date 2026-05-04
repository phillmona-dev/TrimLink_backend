package com.trimlink.module.payment.entity;

public enum PaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
    PENDING,    // initiated, awaiting gateway response
    SUCCESS,    // gateway confirmed payment
    FAILED,     // gateway reported failure
    CANCELLED,  // user abandoned payment
    REFUNDED    // refund processed
}
