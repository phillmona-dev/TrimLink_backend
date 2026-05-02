package com.trimlink.module.payment.entity;

public enum PaymentStatus {
    PENDING,    // initiated, awaiting gateway response
    SUCCESS,    // gateway confirmed payment
    FAILED,     // gateway reported failure
    CANCELLED,  // user abandoned payment
    REFUNDED    // refund processed
}
