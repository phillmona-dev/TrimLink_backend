package com.trimlink.module.notification.dto;

import com.trimlink.module.notification.entity.DevicePlatform;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DeviceTokenResponse {
    private UUID id;
    private DevicePlatform platform;
    private String deviceId;
    private String appVersion;
    private boolean active;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
}
