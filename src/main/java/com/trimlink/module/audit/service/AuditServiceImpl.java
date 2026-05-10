package com.trimlink.module.audit.service;

import com.trimlink.module.audit.dto.RequestMetadata;
import com.trimlink.module.audit.entity.AuditLog;
import com.trimlink.module.audit.repository.AuditLogRepository;
import com.trimlink.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public void logCurrent(String action, String resourceType, String resourceId, String status, String metadata) {
        UUID userId = null;
        String username = "ANONYMOUS";

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            userId = user.getUserId();
            username = user.getPhone();
        }

        // Capture Request Metadata
        RequestMetadata requestMetadata = com.trimlink.common.utils.RequestUtils.captureRequestMetadata();

        // Delegate to the async method
        this.log(userId, username, action, resourceType, resourceId, status, metadata, requestMetadata);
    }

    @Async
    @Override
    public void log(UUID userId, String username, String action, String resourceType, String resourceId, String status, String metadata, RequestMetadata requestMetadata) {
        try {
            AuditLog.AuditLogBuilder logBuilder = AuditLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .status(status)
                    .metadata(metadata);

            if (requestMetadata != null) {
                logBuilder.ipAddress(requestMetadata.getIpAddress())
                        .userAgent(requestMetadata.getUserAgent())
                        .browser(requestMetadata.getBrowser())
                        .os(requestMetadata.getOs())
                        .device(requestMetadata.getDevice())
                        .requestUrl(requestMetadata.getRequestUrl())
                        .requestMethod(requestMetadata.getRequestMethod());
            }

            auditLogRepository.save(logBuilder.build());
            log.debug("Audit log saved: {} by {}", action, username);

        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    @Override
    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    @Override
    public Page<AuditLog> searchLogs(String username, String action, Pageable pageable) {
        return auditLogRepository.searchLogs(username, action, pageable);
    }

    @Override
    public Page<AuditLog> getLogsByUser(UUID userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable);
    }
}
