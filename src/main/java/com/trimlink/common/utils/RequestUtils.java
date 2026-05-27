package com.trimlink.common.utils;

import com.trimlink.module.audit.dto.RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class RequestUtils {

    public static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    public static String getClientIp() {
        HttpServletRequest request = getCurrentRequest();
        return request != null ? getClientIp(request) : "0.0.0.0";
    }

    public static RequestMetadata captureRequestMetadata() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return null;

        HttpServletRequest request = attributes.getRequest();
        String userAgentString = request.getHeader("User-Agent");
        
        eu.bitwalker.useragentutils.UserAgent ua = eu.bitwalker.useragentutils.UserAgent.parseUserAgentString(userAgentString);
        
        return RequestMetadata.builder()
                .ipAddress(getClientIp(request))
                .userAgent(userAgentString)
                .browser(ua.getBrowser().getName())
                .os(ua.getOperatingSystem().getName())
                .device(ua.getOperatingSystem().getDeviceType().getName())
                .requestUrl(request.getRequestURL().toString())
                .requestMethod(request.getMethod())
                .build();
    }

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // Normalize IPv6 loopback to IPv4 loopback for readability
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }
        
        return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }
}
