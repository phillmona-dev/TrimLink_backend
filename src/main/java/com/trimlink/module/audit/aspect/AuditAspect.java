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
        String resourceId = extractResourceId(joinPoint);
        String usernameFromArgs = extractUsername(joinPoint);
        
        Object result;
        try {
            result = joinPoint.proceed();
            
            // Log success
            if (usernameFromArgs != null) {
                // If we found a username in args (like login), use it explicitly
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

    private String extractResourceId(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();

            if (args == null || args.length == 0) return null;

            // 1. Look for parameter named 'id' or ending with 'Id' or 'Uuid'
            for (int i = 0; i < parameterNames.length; i++) {
                String name = parameterNames[i].toLowerCase();
                if (name.equals("id") || name.endsWith("id") || name.endsWith("uuid")) {
                    return String.valueOf(args[i]);
                }
            }

            // 2. Default to first argument if it's a simple type
            Object firstArg = args[0];
            if (firstArg instanceof String || firstArg instanceof java.util.UUID || firstArg instanceof Long) {
                return String.valueOf(firstArg);
            }
        } catch (Exception e) {
            log.warn("Could not extract resource ID for audit: {}", e.getMessage());
        }
        return null;
    }
}
