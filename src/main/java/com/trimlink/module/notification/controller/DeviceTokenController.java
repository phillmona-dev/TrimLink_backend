package com.trimlink.module.notification.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.module.notification.dto.DeviceTokenResponse;
import com.trimlink.module.notification.dto.RegisterDeviceTokenRequest;
import com.trimlink.module.notification.service.DeviceTokenService;
import com.trimlink.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Notification Devices", description = "Manage push notification device tokens")
@RestController
@RequestMapping("/notifications/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @Operation(summary = "Register or refresh the current device token")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceTokenResponse>> register(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        DeviceTokenResponse response = deviceTokenService.register(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "List my registered devices")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DeviceTokenResponse>>> list(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(deviceTokenService.listForUser(principal.getUserId())));
    }

    @Operation(summary = "Deactivate one of my registered devices")
    @DeleteMapping("/{deviceTokenId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID deviceTokenId) {
        deviceTokenService.deactivate(principal.getUserId(), deviceTokenId);
        return ResponseEntity.ok(ApiResponse.ok("Device token deactivated", null));
    }
}
