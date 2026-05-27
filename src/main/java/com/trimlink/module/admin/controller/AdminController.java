package com.trimlink.module.admin.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.common.dto.PageResponse;
import com.trimlink.module.admin.dto.BarberPerformanceResponse;
import com.trimlink.module.admin.dto.DashboardStats;
import com.trimlink.module.admin.service.AdminService;
import com.trimlink.module.audit.service.AuditQueryService;
import com.trimlink.module.audit.service.AuditService;
import com.trimlink.module.audit.entity.AuditLog;
import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.shop.entity.BarberShop;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.trimlink.module.audit.service.AuditService;
import com.trimlink.module.audit.entity.AuditLog;
import com.trimlink.module.booking.dto.AppointmentResponse;
import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.admin.dto.AdminAppointmentStats;
import com.trimlink.module.admin.dto.ShopFinanceSummary;
import com.trimlink.module.admin.dto.TransactionResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin", description = "Admin dashboard and management APIs")
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AuditService auditService;
    private final AuditQueryService auditQueryService;

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
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> listAppointments(
            @RequestParam(required = false) UUID shopId,
            @RequestParam(required = false) UUID barberId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "scheduledStart", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(PageResponse.from(adminService.listAllAppointments(shopId, barberId, status, startDate, endDate, query, pageable))));
    }

    @Operation(summary = "Get platform-wide appointment financial stats")
    @GetMapping("/appointments/stats")
    public ResponseEntity<ApiResponse<AdminAppointmentStats>> getAppointmentStats() {
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

    @Operation(summary = "Get shop-wise financial summaries")
    @GetMapping("/finance/shops")
    public ResponseEntity<ApiResponse<List<ShopFinanceSummary>>> getShopFinanceSummaries() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getShopFinanceSummaries()));
    }

    @Operation(summary = "List all financial transactions")
    @GetMapping("/finance/transactions")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> listTransactions(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(adminService.listTransactions(pageable))));
    }

    @Operation(summary = "Update a platform setting")
    @PatchMapping("/settings/{key}")
    public ResponseEntity<ApiResponse<Void>> updateSetting(
            @PathVariable String key,
            @RequestParam String value) {
        adminService.updateSetting(key, value);
        return ResponseEntity.ok(ApiResponse.ok("Setting updated successfully", null));
    }

    @Operation(summary = "List all platform audit logs (filtered)")
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> listAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(PageResponse.from(auditService.searchLogs(username, action, pageable))));
    }

    // --- Time Machine History Endpoints ---

    @Operation(summary = "Get history of a specific appointment")
    @GetMapping("/audit/history/appointments/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<AuditQueryService.EntityRevisionDTO>>> getAppointmentHistory(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(auditQueryService.getRevisions(Appointment.class, id)));
    }

    @Operation(summary = "Get history of a specific shop")
    @GetMapping("/audit/history/shops/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<AuditQueryService.EntityRevisionDTO>>> getShopHistory(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(auditQueryService.getRevisions(BarberShop.class, id)));
    }

    @Operation(summary = "Get history of a user profile")
    @GetMapping("/audit/history/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditQueryService.EntityRevisionDTO>>> getUserHistory(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(auditQueryService.getRevisions(User.class, id)));
    }
}
