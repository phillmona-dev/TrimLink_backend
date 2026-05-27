package com.trimlink.module.audit.aspect;

import com.trimlink.module.audit.annotation.AuditAction;
import com.trimlink.module.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(auditAction)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditAction auditAction) throws Throwable {
        String action = auditAction.action();
        String resourceType = auditAction.resource();
        
        // Try to find an ID and Username in the arguments
        String resourceId = extractResourceId(joinPoint, resourceType);
        String usernameFromArgs = extractUsername(joinPoint);
        
        Object result;
        try {
            result = joinPoint.proceed();
            
            // For CREATE actions, the ID in arguments might be a parent ID (e.g. customerId).
            // We should prioritize the ID from the result (the new entity ID).
            if ((action.contains("CREATE") || resourceId == null) && result != null) {
                String resultId = extractIdFromObject(result);
                if (resultId != null) {
                    log.debug("Audit: Extracted ID from result: {} (was: {})", resultId, resourceId);
                    resourceId = resultId;
                }
            }
            
            log.debug("Audit: Logging action={} resourceType={} resourceId={}", action, resourceType, resourceId);
            
            // Log success
            if (usernameFromArgs != null) {
                auditService.log(null, usernameFromArgs, action, resourceType, resourceId, "SUCCESS", "Execution successful", com.trimlink.common.utils.RequestUtils.captureRequestMetadata());
            } else {
                auditService.logCurrent(action, resourceType, resourceId, "SUCCESS", "Execution successful");
            }
            
            return result;
        } catch (Throwable throwable) {
            // Log failure automatically
            if (usernameFromArgs != null) {
                auditService.log(null, usernameFromArgs, action, resourceType, resourceId, "FAILURE", throwable.getMessage(), com.trimlink.common.utils.RequestUtils.captureRequestMetadata());
            } else {
                auditService.logCurrent(action, resourceType, resourceId, "FAILURE", throwable.getMessage());
            }
            throw throwable;
        }
    }

    private String extractUsername(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            for (Object arg : args) {
                if (arg == null) continue;
                // Reflection to find 'username' or 'getUsername()' or 'phone' or 'getPhone()'
                try {
                    var method = arg.getClass().getMethod("getUsername");
                    return String.valueOf(method.invoke(arg));
                } catch (NoSuchMethodException e) {
                    try {
                        var method = arg.getClass().getMethod("getPhone");
                        return String.valueOf(method.invoke(arg));
                    } catch (NoSuchMethodException e2) {
                        // Continue search
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract username from arguments: {}", e.getMessage());
        }
        return null;
    }

    private String extractResourceId(ProceedingJoinPoint joinPoint, String resourceType) {
        try {
            Object[] args = joinPoint.getArgs();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();

            if (args == null || args.length == 0) return null;

            // 1. Look for parameter named exactly 'id'
            for (int i = 0; i < parameterNames.length; i++) {
                if (parameterNames[i].equalsIgnoreCase("id")) {
                    return String.valueOf(args[i]);
                }
            }

            // 2. Look for parameter that matches the resource type (e.g. 'appointmentId' for 'BOOKING')
            // This handles cases like 'appointmentId' or 'bookingId' or 'shopId'
            String resourcePrefix = resourceType.toLowerCase();
            for (int i = 0; i < parameterNames.length; i++) {
                String name = parameterNames[i].toLowerCase();
                if (name.contains(resourcePrefix) && (name.endsWith("id") || name.endsWith("uuid"))) {
                    return String.valueOf(args[i]);
                }
            }

            // 3. Fallback to any parameter ending with 'Id' or 'Uuid' (but NOT if it starts with common prefixes like 'customer' or 'user' unless resource is USER)
            for (int i = 0; i < parameterNames.length; i++) {
                String name = parameterNames[i].toLowerCase();
                if (name.endsWith("id") || name.endsWith("uuid")) {
                    // Skip 'customerId' if we are looking for 'BOOKING'
                    if (name.startsWith("customer") && !resourceType.equalsIgnoreCase("USER") && !resourceType.equalsIgnoreCase("CUSTOMER")) {
                        continue;
                    }
                    return String.valueOf(args[i]);
                }
            }

            // 4. Default to first argument if it's a simple type
            Object firstArg = args[0];
            if (firstArg instanceof String || firstArg instanceof java.util.UUID || firstArg instanceof Long) {
                return String.valueOf(firstArg);
            }
        } catch (Exception e) {
            log.warn("Could not extract resource ID for audit: {}", e.getMessage());
        }
        return null;
    }

    private String extractIdFromObject(Object obj) {
        if (obj == null) return null;
        try {
            // Try getId()
            var method = obj.getClass().getMethod("getId");
            return String.valueOf(method.invoke(obj));
        } catch (Exception e) {
            // Silently fail if no ID method found
            return null;
        }
    }
}
