package com.trimlink.module.audit.repository;

import com.trimlink.module.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    
    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM AuditLog a WHERE " +
            "(:username IS NULL OR a.username LIKE %:username%) AND " +
            "(:action IS NULL OR a.action = :action)")
    Page<AuditLog> searchLogs(String username, String action, Pageable pageable);

    Page<AuditLog> findByAction(String action, Pageable pageable);
    
    Page<AuditLog> findByResourceType(String resourceType, Pageable pageable);
}
