package com.trimlink.module.user.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.module.user.dto.BarberScheduleRequest;
import com.trimlink.module.user.dto.BarberScheduleResponse;
import com.trimlink.module.user.dto.BreakTimeRequest;
import com.trimlink.module.user.service.BarberScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

@Tag(name = "Barber Schedule", description = "Barber working hours and break time management")
@RestController
@RequestMapping("/barber/schedule")
@RequiredArgsConstructor
public class BarberScheduleController {

    private final BarberScheduleService scheduleService;

    // GET /barber/schedule/{barberId}/week — full 7-day schedule
    @Operation(summary = "Get full week schedule for a barber")
    @GetMapping("/{barberId}/week")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BarberScheduleResponse>>> getWeekSchedule(
            @PathVariable UUID barberId) {
        return ResponseEntity.ok(ApiResponse.ok(scheduleService.getWeekSchedule(barberId)));
    }

    // GET /barber/schedule/{barberId}/day?day=MONDAY
    @Operation(summary = "Get schedule for a specific day")
    @GetMapping("/{barberId}/day")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BarberScheduleResponse>> getDaySchedule(
            @PathVariable UUID barberId,
            @RequestParam DayOfWeek day) {
        return ResponseEntity.ok(ApiResponse.ok(scheduleService.getDaySchedule(barberId, day)));
    }

    // PUT /barber/schedule/{barberId} — upsert a day's schedule
    @Operation(summary = "Set or update schedule for a specific day")
    @PutMapping("/{barberId}")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BarberScheduleResponse>> upsertSchedule(
            @PathVariable UUID barberId,
            @Valid @RequestBody BarberScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                scheduleService.upsertSchedule(barberId, request)));
    }

    // POST /barber/schedule/{scheduleId}/breaks — add a break
    @Operation(summary = "Add a break time to a schedule")
    @PostMapping("/{scheduleId}/breaks")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BarberScheduleResponse>> addBreak(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody BreakTimeRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(scheduleService.addBreak(scheduleId, request)));
    }

    // DELETE /barber/schedule/{scheduleId}/breaks/{breakId}
    @Operation(summary = "Remove a break time")
    @DeleteMapping("/{scheduleId}/breaks/{breakId}")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BarberScheduleResponse>> removeBreak(
            @PathVariable UUID scheduleId,
            @PathVariable UUID breakId) {
        return ResponseEntity.ok(ApiResponse.ok(
                scheduleService.removeBreak(scheduleId, breakId)));
    }

    // DELETE /barber/schedule/{scheduleId}
    @Operation(summary = "Delete a barber's day schedule (reverts to shop hours)")
    @DeleteMapping("/{scheduleId}")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(@PathVariable UUID scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.ok(ApiResponse.ok("Schedule deleted. Shop hours will be used.", null));
    }
}
