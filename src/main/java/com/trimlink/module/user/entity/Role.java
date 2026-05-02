package com.trimlink.module.user.entity;

/**
 * System-wide roles. Used both in JWT claims and @PreAuthorize expressions.
 */
public enum Role {
    CUSTOMER,   // end users booking appointments
    BARBER,     // individual barbers
    OWNER,      // barbershop owners
    ADMIN       // platform administrators
}
