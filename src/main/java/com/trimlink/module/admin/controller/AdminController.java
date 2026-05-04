package com.trimlink.module.admin.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.common.dto.PageResponse;
import com.trimlink.module.admin.dto.BarberPerformanceResponse;
import com.trimlink.module.admin.dto.DashboardStats;
import com.trimlink.module.admin.service.AdminService;
import com.trimlink.module.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin", description = "Admin dashboard and management APIs")
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "Get platform dashboard statistics")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStats>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getDashboardStats()));
    }

    @Operation(summary = "List all users (paginated)")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> listUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(PageResponse.from(adminService.listUsers(pageable))));
    }

    @Operation(summary = "List barber performance metrics")
    @GetMapping("/barbers/performance")
    public ResponseEntity<ApiResponse<PageResponse<BarberPerformanceResponse>>> listBarberPerformance(
            @PageableDefault(size = 20, sort = "averageRating") Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(PageResponse.from(adminService.listBarberPerformance(pageable))));
    }

    @Operation(summary = "List all platform appointments (filtered)")
    @GetMapping("/appointments")
    public ResponseEntity<ApiResponse<PageResponse<com.trimlink.module.booking.dto.AppointmentResponse>>> listAppointments(
            @RequestParam(required = false) UUID shopId,
            @RequestParam(required = false) UUID barberId,
            @RequestParam(required = false) com.trimlink.module.booking.entity.AppointmentStatus status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "scheduledStart", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(PageResponse.from(adminService.listAllAppointments(shopId, barberId, status, startDate, endDate, query, pageable))));
    }

    @Operation(summary = "Get platform-wide appointment financial stats")
    @GetMapping("/appointments/stats")
    public ResponseEntity<ApiResponse<com.trimlink.module.admin.dto.AdminAppointmentStats>> getAppointmentStats() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getAppointmentStats()));
    }

    @Operation(summary = "Get all pending shops awaiting approval")
    @GetMapping("/users/pending")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getPendingShops() {
        return ResponseEntity.ok(ApiResponse.ok("Pending shops retrieved", adminService.getPendingShops()));
    }

    @Operation(summary = "Approve a pending user account")
    @PatchMapping("/users/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approveUser(@PathVariable java.util.UUID id) {
        adminService.approveUser(id);
        return ResponseEntity.ok(ApiResponse.ok("User approved successfully", null));
    }

    @Operation(summary = "Reject a pending user account")
    @PatchMapping("/users/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectUser(@PathVariable java.util.UUID id) {
        adminService.rejectUser(id);
        return ResponseEntity.ok(ApiResponse.ok("User rejected successfully", null));
    }

    @Operation(summary = "Update a platform setting")
    @PatchMapping("/settings/{key}")
    public ResponseEntity<ApiResponse<Void>> updateSetting(
            @PathVariable String key,
            @RequestParam String value) {
        adminService.updateSetting(key, value);
        return ResponseEntity.ok(ApiResponse.ok("Setting updated successfully", null));
    }
}
