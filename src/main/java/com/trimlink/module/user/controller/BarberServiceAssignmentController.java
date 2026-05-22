package com.trimlink.module.user.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.module.user.dto.BarberServiceAssignmentResponse;
import com.trimlink.module.user.dto.UpsertBarberServicesRequest;
import com.trimlink.module.user.service.BarberServiceAssignmentService;
import com.trimlink.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Barber Services", description = "Manage services assigned to a barber profile")
@RestController
@RequestMapping("/barber/services")
@RequiredArgsConstructor
public class BarberServiceAssignmentController {

    private final BarberServiceAssignmentService assignmentService;

    @Operation(summary = "List active services assigned to a barber")
    @GetMapping("/{barberId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BarberServiceAssignmentResponse>>> listAssignments(
            @PathVariable UUID barberId) {
        return ResponseEntity.ok(ApiResponse.ok(assignmentService.listAssignments(barberId)));
    }

    @Operation(summary = "Assign or update one or more services for a barber")
    @PutMapping("/{barberId}")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<BarberServiceAssignmentResponse>>> upsertAssignments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID barberId,
            @Valid @RequestBody UpsertBarberServicesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                assignmentService.upsertAssignments(barberId, principal.getUserId(), principal.getRole(), request)));
    }

    @Operation(summary = "Deactivate a barber service assignment")
    @DeleteMapping("/{barberId}/{assignmentId}")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BarberServiceAssignmentResponse>> deactivateAssignment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID barberId,
            @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(ApiResponse.ok(
                assignmentService.deactivateAssignment(
                        barberId, assignmentId, principal.getUserId(), principal.getRole())));
    }

    @Operation(summary = "Update haircut styles attached to a service assignment")
    @PutMapping("/{barberId}/assignments/{assignmentId}/styles")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BarberServiceAssignmentResponse>> updateStyleImages(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID barberId,
            @PathVariable UUID assignmentId,
            @RequestBody List<String> styleImageUrls) {
        return ResponseEntity.ok(ApiResponse.ok(
                assignmentService.updateStyleImages(
                        barberId, assignmentId, styleImageUrls, principal.getUserId(), principal.getRole())));
    }
}
