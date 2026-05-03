package com.trimlink.module.user.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.module.user.dto.StaffServiceAssignmentResponse;
import com.trimlink.module.user.dto.UpsertStaffServicesRequest;
import com.trimlink.module.user.service.StaffServiceAssignmentService;
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

@Tag(name = "Staff Services", description = "Manage services assigned to a staff profile")
@RestController
@RequestMapping("/staff/services")
@RequiredArgsConstructor
public class StaffServiceAssignmentController {

    private final StaffServiceAssignmentService assignmentService;

    @Operation(summary = "List active services assigned to a staff")
    @GetMapping("/{staffId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<StaffServiceAssignmentResponse>>> listAssignments(
            @PathVariable UUID staffId) {
        return ResponseEntity.ok(ApiResponse.ok(assignmentService.listAssignments(staffId)));
    }

    @Operation(summary = "Assign or update one or more services for a staff")
    @PutMapping("/{staffId}")
    @PreAuthorize("hasAnyRole('STAFF', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<StaffServiceAssignmentResponse>>> upsertAssignments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID staffId,
            @Valid @RequestBody UpsertStaffServicesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                assignmentService.upsertAssignments(staffId, principal.getUserId(), principal.getRole(), request)));
    }

    @Operation(summary = "Deactivate a staff service assignment")
    @DeleteMapping("/{staffId}/{assignmentId}")
    @PreAuthorize("hasAnyRole('STAFF', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<StaffServiceAssignmentResponse>> deactivateAssignment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID staffId,
            @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(ApiResponse.ok(
                assignmentService.deactivateAssignment(
                        staffId, assignmentId, principal.getUserId(), principal.getRole())));
    }
}
