package com.trimlink.module.payment.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.module.payment.dto.InitiatePaymentRequest;
import com.trimlink.module.payment.dto.PaymentResponse;
import com.trimlink.module.payment.service.PaymentService;
import com.trimlink.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Payments", description = "Payment initiation and status tracking")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // POST /payments/initiate
    @Operation(summary = "Initiate a payment (Chapa or Telebirr)")
    @PostMapping("/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody InitiatePaymentRequest request) {

        PaymentResponse response = paymentService.initiatePayment(principal.getUserId(), request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    // GET /payments/{id}
    @Operation(summary = "Get payment status by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentResponse>> getStatus(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.getPaymentStatus(principal.getUserId(), principal.getRole(), id)));
    }

    @Operation(summary = "Manually trigger payment reconciliation (sync with provider)")
    @PostMapping("/{id}/reconcile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentResponse>> reconcile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.manualReconcile(principal.getUserId(), principal.getRole(), id)));
    }
}
