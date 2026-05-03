package com.trimlink.module.user.entity;

/**
 * System-wide roles. Used both in JWT claims and @PreAuthorize expressions.
 */
public enum Role {
    CUSTOMER,   // end users booking appointments
    STAFF,      // staff members (staffs, washers, etc.)
    OWNER,      // staffshop owners
    ADMIN       // platform administrators
}
