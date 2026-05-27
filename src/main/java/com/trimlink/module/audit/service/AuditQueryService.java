package com.trimlink.module.audit.service;

import com.trimlink.common.audit.UserRevisionEntity;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final EntityManager entityManager;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Fetches all revisions for a specific entity.
     */
    @Transactional(readOnly = true)
    public List<EntityRevisionDTO> getRevisions(Class<?> entityClass, Object entityId) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        // Get all revision numbers for this entity
        List<Number> revisionNumbers = auditReader.getRevisions(entityClass, entityId);

        return revisionNumbers.stream().map(revNum -> {
            // Fetch the actual entity state at that revision
            Object entity = auditReader.find(entityClass, entityId, revNum);
            
            // Fetch the metadata (who, when) for that revision
            UserRevisionEntity revEntity = auditReader.findRevision(UserRevisionEntity.class, revNum);

            // Convert entity to Map safely
            Map<String, Object> entityData = safeConvertToMap(entity);

            return EntityRevisionDTO.builder()
                    .revisionNumber(revNum.intValue())
                    .revisionTimestamp(revEntity.getTimestamp())
                    .userId(revEntity.getUserId())
                    .username(revEntity.getUsername())
                    .ipAddress(revEntity.getIpAddress())
                    .entity(entityData)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Safely converts a JPA entity to a Map, avoiding proxy initialization issues.
     */
    private Map<String, Object> safeConvertToMap(Object entity) {
        if (entity == null) return null;
        
        try {
            // We use Jackson but with a custom configuration to handle proxies as just their IDs
            Map<String, Object> map = new java.util.HashMap<>();
            
            // Use reflection to get fields to avoid deep serialization issues
            java.lang.reflect.Field[] fields = entity.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(entity);
                
                if (value == null) {
                    map.put(field.getName(), null);
                    continue;
                }
                
                // If it's a Hibernate Proxy, just take the ID
                if (value instanceof org.hibernate.proxy.HibernateProxy) {
                    Object id = ((org.hibernate.proxy.HibernateProxy) value).getHibernateLazyInitializer().getIdentifier();
                    map.put(field.getName() + "Id", id);
                } 
                // If it's a simple type or collection of simple types, take as is
                else if (isSimpleType(value.getClass())) {
                    map.put(field.getName(), value);
                }
                // For other objects (associations), try to get their ID if they have one
                else {
                    Object id = tryGetId(value);
                    if (id != null) {
                        map.put(field.getName() + "Id", id);
                    } else {
                        // Fallback to string representation to avoid circular refs
                        map.put(field.getName(), value.toString());
                    }
                }
            }
            return map;
        } catch (Exception e) {
            return java.util.Map.of("error", "Could not serialize entity state: " + e.getMessage());
        }
    }

    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive() || 
               clazz.equals(String.class) || 
               clazz.equals(UUID.class) || 
               clazz.equals(Integer.class) || 
               clazz.equals(Long.class) || 
               clazz.equals(Double.class) || 
               clazz.equals(java.math.BigDecimal.class) || 
               clazz.equals(Boolean.class) ||
               clazz.equals(java.time.LocalDateTime.class) ||
               clazz.equals(java.time.LocalDate.class) ||
               clazz.isEnum();
    }

    private Object tryGetId(Object obj) {
        try {
            var method = obj.getClass().getMethod("getId");
            return method.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class EntityRevisionDTO {
        private int revisionNumber;
        private long revisionTimestamp;
        private UUID userId;
        private String username;
        private String ipAddress;
        private Object entity;
    }
}
