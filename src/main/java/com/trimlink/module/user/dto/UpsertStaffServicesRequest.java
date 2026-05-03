package com.trimlink.module.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpsertStaffServicesRequest {

    @Valid
    @NotEmpty(message = "At least one service assignment is required")
    private List<StaffServiceAssignmentRequest> assignments;
}
