package com.trimlink.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Immutable principal object stored in the SecurityContext.
 * Accessible via SecurityContextHolder in any service layer.
 */
@Getter
@AllArgsConstructor
public class AuthenticatedUser {
    private final UUID userId;
    private final String phone;
    private final String role;
}
