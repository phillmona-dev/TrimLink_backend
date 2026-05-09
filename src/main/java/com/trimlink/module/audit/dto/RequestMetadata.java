package com.trimlink.module.audit.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RequestMetadata {
    private String ipAddress;
    private String userAgent;
    private String browser;
    private String os;
    private String device;
    private String requestUrl;
    private String requestMethod;
}
