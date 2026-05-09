package com.trimlink.module.audit.listener;

import com.trimlink.module.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditSecurityListener {

    private final AuditService auditService;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        auditService.log(null, username, "LOGIN", "USER", null, "SUCCESS", "User logged in: " + username, com.trimlink.common.utils.RequestUtils.captureRequestMetadata());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String error = event.getException().getMessage();
        auditService.log(null, username, "LOGIN", "USER", null, "FAILURE", "Failed login for " + username + ": " + error, com.trimlink.common.utils.RequestUtils.captureRequestMetadata());
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        String username = event.getAuthentication().getName();
        auditService.log(null, username, "LOGOUT", "USER", null, "SUCCESS", "User logged out: " + username, com.trimlink.common.utils.RequestUtils.captureRequestMetadata());
    }
}
