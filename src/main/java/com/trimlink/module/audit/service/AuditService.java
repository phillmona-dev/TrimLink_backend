    package com.trimlink.module.audit.service;

import com.trimlink.module.audit.dto.RequestMetadata;
import com.trimlink.module.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AuditService {
    
    /**
     * Records a new audit log entry asynchronously with full metadata.
     */
    void log(UUID userId, String username, String action, String resourceType, String resourceId, String status, String metadata, RequestMetadata requestMetadata);

    /**
     * Captures current user and request context from SecurityContext and HttpServletRequest.
     */
    void logCurrent(String action, String resourceType, String resourceId, String status, String metadata);

    /**
     * Retrieves all logs with pagination.
     */
    Page<AuditLog> getAllLogs(Pageable pageable);

    Page<AuditLog> searchLogs(String username, String action, Pageable pageable);

    /**
     * Retrieves logs for a specific user.
     */
    Page<AuditLog> getLogsByUser(UUID userId, Pageable pageable);
}
