package com.trimlink.module.user.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.user.dto.BarberResponse;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.booking.dto.PublicScheduleBlockResponse;
import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.user.repository.BarberProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
    public ResponseEntity<ApiResponse<Page<BarberResponse>>> searchBarbers(
            @RequestParam(required = false) String q,
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        
       Page<BarberProfile> barbers = barberProfileRepository.searchActiveWithUser(q != null ? q : "", pageable);
        Page<BarberResponse> responses = barbers.map(barber -> {
            boolean isBusy = appointmentRepository.existsByBarberIdAndStatusAndDeletedFalse(
                    barber.getId(), com.trimlink.module.booking.entity.AppointmentStatus.IN_PROGRESS);
            return BarberResponse.from(barber, isBusy ? "BUSY" : "IDLE");
        });
        
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @Operation(summary = "Get barber day schedule blocks for booking UI (public, time ranges only)")
    @GetMapping("/{id}/day-schedule")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<PublicScheduleBlockResponse>>> getDaySchedule(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        BarberProfile barber = barberProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Barber", "id", id));

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(LocalTime.MAX);
        List<Appointment> appointments = appointmentRepository.findBarberDaySchedule(barber.getId(), dayStart, dayEnd);

        List<PublicScheduleBlockResponse> blocks = appointments.stream()
                .map(a -> PublicScheduleBlockResponse.builder()
                        .scheduledStart(a.getScheduledStart())
                        .scheduledEnd(a.getScheduledEnd())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(blocks));
    }
}
