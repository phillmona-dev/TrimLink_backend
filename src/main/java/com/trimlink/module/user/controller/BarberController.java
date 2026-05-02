package com.trimlink.module.user.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.user.dto.BarberResponse;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.repository.BarberProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Barbers", description = "Barber profile management")
@RestController
@RequestMapping("/barbers")
@RequiredArgsConstructor
public class BarberController {

    private final BarberProfileRepository barberProfileRepository;

    @Operation(summary = "Get barber profile by ID")
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<BarberResponse>> getById(@PathVariable UUID id) {
        BarberProfile barber = barberProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Barber", "id", id));
        return ResponseEntity.ok(ApiResponse.ok(BarberResponse.from(barber)));
    }
}
