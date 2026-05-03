package com.trimlink.module.user.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.user.dto.StaffResponse;
import com.trimlink.module.user.entity.StaffProfile;
import com.trimlink.module.user.repository.StaffProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Staffs", description = "Staff profile management")
@RestController
@RequestMapping("/staffs")
@RequiredArgsConstructor
public class StaffController {

    private final StaffProfileRepository staffProfileRepository;

    @Operation(summary = "Get staff profile by ID")
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<StaffResponse>> getById(@PathVariable UUID id) {
        StaffProfile staff = staffProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", id));
        return ResponseEntity.ok(ApiResponse.ok(StaffResponse.from(staff)));
    }
}
