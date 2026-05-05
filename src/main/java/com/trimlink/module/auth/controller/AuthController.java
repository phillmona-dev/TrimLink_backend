package com.trimlink.module.auth.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.module.auth.dto.*;
import com.trimlink.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Phone OTP authentication flow")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse auth = authService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("Registration successful", auth));
    }

    @Operation(summary = "Shop registration submitted for approval")
    @PostMapping("/register/shop")
    public ResponseEntity<ApiResponse<AuthResponse>> registerShop(
            @Valid @RequestBody ShopRegistrationRequest request) {

        AuthResponse auth = authService.registerShop(request);
        return ResponseEntity.ok(ApiResponse.ok("Shop registration submitted for approval", auth));
    }

    @Operation(summary = "Complete shop registration for authenticated users (e.g. Google users)")
    @PostMapping("/complete-shop-registration")
    public ResponseEntity<ApiResponse<AuthResponse>> completeShopRegistration(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.trimlink.security.AuthenticatedUser principal,
            @Valid @RequestBody CompleteShopRegistrationRequest request) {

        AuthResponse auth = authService.completeShopRegistration(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Shop setup submitted for approval", auth));
    }

    @Operation(summary = "Login with username and password")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", auth));
    }

    @Operation(summary = "Refresh access token using refresh token")
    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        AuthResponse auth = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", auth));
    }
}
