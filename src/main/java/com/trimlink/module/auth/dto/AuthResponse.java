package com.trimlink.module.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private UUID userId;
    private String accessToken;
    private String refreshToken;
    private long accessTokenExpiresIn;   // seconds
    private String phone;
    private String role;
    private boolean newUser;
}
