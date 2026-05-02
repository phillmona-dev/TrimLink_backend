package com.trimlink.module.notification.dto;

import com.trimlink.module.notification.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDeviceTokenRequest {

    @NotBlank
    @Size(max = 512)
    private String token;

    @NotNull
    private DevicePlatform platform;

    @Size(max = 150)
    private String deviceId;

    @Size(max = 50)
    private String appVersion;
}
