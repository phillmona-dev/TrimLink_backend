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
    private final com.trimlink.module.booking.repository.AppointmentRepository appointmentRepository;

    @Operation(summary = "Get barber profile by ID")
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<BarberResponse>> getById(@PathVariable UUID id) {
        BarberProfile barber = barberProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Barber", "id", id));
        
        boolean isBusy = appointmentRepository.existsByBarberIdAndStatusAndDeletedFalse(
                barber.getId(), com.trimlink.module.booking.entity.AppointmentStatus.IN_PROGRESS);
        
        return ResponseEntity.ok(ApiResponse.ok(BarberResponse.from(barber, isBusy ? "BUSY" : "IDLE")));
    }

    @Operation(summary = "Search barbers")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<BarberResponse>>> searchBarbers(
            @RequestParam(required = false) String q,
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        
        org.springframework.data.domain.Page<BarberProfile> barbers = barberProfileRepository.searchActiveWithUser(q != null ? q : "", pageable);
        org.springframework.data.domain.Page<BarberResponse> responses = barbers.map(barber -> {
            boolean isBusy = appointmentRepository.existsByBarberIdAndStatusAndDeletedFalse(
                    barber.getId(), com.trimlink.module.booking.entity.AppointmentStatus.IN_PROGRESS);
            return BarberResponse.from(barber, isBusy ? "BUSY" : "IDLE");
        });
        
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }
}
