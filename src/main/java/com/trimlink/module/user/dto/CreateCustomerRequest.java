package com.trimlink.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCustomerRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @Size(max = 20)
    private String phoneNumber;

    @Email
    @Size(max = 150)
    private String email;

    @Size(min = 6)
    private String password;

    private String avatarUrl;
}
