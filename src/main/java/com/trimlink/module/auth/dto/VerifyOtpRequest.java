package com.trimlink.module.auth.dto;

import com.trimlink.module.user.entity.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+2519[0-9]{8}$|^09[0-9]{8}$",
             message = "Enter a valid Ethiopian phone number (e.g. +251912345678 or 0912345678)")
    private String phoneNumber;

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
    @Pattern(regexp = "\\d{6}", message = "OTP must contain only digits")
    private String otp;

    // For new users only — optional on re-login
    private String firstName;
    private String lastName;

    // Default to CUSTOMER if not provided
    private Role role = Role.CUSTOMER;
}
