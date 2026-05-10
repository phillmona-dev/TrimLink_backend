package com.trimlink.module.audit.controller;

import com.trimlink.module.audit.entity.AuditLog;
import com.trimlink.module.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/audit-logs")
@Tag(name = "Audit Logs", description = "Admin interface for monitoring system activity")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Get all audit logs (Admin only)")
    public ResponseEntity<com.trimlink.common.dto.ApiResponse<Page<AuditLog>>> getAllLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @PageableDefault(size = 20, sort = "timestamp", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        
        Page<AuditLog> logs = auditService.searchLogs(username, action, pageable);
        return ResponseEntity.ok(com.trimlink.common.dto.ApiResponse.ok(logs));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get audit logs for a specific user")
    public ResponseEntity<com.trimlink.common.dto.ApiResponse<Page<AuditLog>>> getLogsByUser(
            @PathVariable UUID userId,
            @PageableDefault(size = 20, sort = "timestamp", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(com.trimlink.common.dto.ApiResponse.ok(auditService.getLogsByUser(userId, pageable)));
    }
}
