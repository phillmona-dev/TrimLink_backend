package com.trimlink.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+2519[0-9]{8}$|^09[0-9]{8}$",
             message = "Enter a valid Ethiopian phone number (e.g. +251912345678 or 0912345678)")
    private String phoneNumber;
}
