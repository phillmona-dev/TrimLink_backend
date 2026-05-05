package com.trimlink.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompleteShopRegistrationRequest {
    @NotBlank(message = "Shop name is required")
    private String shopName;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Address is required")
    private String address;

    private String shopDescription;
    private Double latitude;
    private Double longitude;
}
