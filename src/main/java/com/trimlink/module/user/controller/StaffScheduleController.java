package com.trimlink.module.user.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.module.user.dto.StaffScheduleRequest;
import com.trimlink.module.user.dto.StaffScheduleResponse;
import com.trimlink.module.user.dto.BreakTimeRequest;
import com.trimlink.module.user.service.StaffScheduleService;
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

@Tag(name = "Staff Schedule", description = "Staff working hours and break time management")
@RestController
@RequestMapping("/staff/schedule")
@RequiredArgsConstructor
public class StaffScheduleController {

    private final StaffScheduleService scheduleService;

    // GET /staff/schedule/{staffId}/week — full 7-day schedule
    @Operation(summary = "Get full week schedule for a staff")
    @GetMapping("/{staffId}/week")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<StaffScheduleResponse>>> getWeekSchedule(
            @PathVariable UUID staffId) {
        return ResponseEntity.ok(ApiResponse.ok(scheduleService.getWeekSchedule(staffId)));
    }

    // GET /staff/schedule/{staffId}/day?day=MONDAY
    @Operation(summary = "Get schedule for a specific day")
    @GetMapping("/{staffId}/day")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StaffScheduleResponse>> getDaySchedule(
            @PathVariable UUID staffId,
            @RequestParam DayOfWeek day) {
        return ResponseEntity.ok(ApiResponse.ok(scheduleService.getDaySchedule(staffId, day)));
    }

    // PUT /staff/schedule/{staffId} — upsert a day's schedule
    @Operation(summary = "Set or update schedule for a specific day")
    @PutMapping("/{staffId}")
    @PreAuthorize("hasAnyRole('STAFF', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<StaffScheduleResponse>> upsertSchedule(
            @PathVariable UUID staffId,
            @Valid @RequestBody StaffScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                scheduleService.upsertSchedule(staffId, request)));
    }

    // POST /staff/schedule/{scheduleId}/breaks — add a break
    @Operation(summary = "Add a break time to a schedule")
    @PostMapping("/{scheduleId}/breaks")
    @PreAuthorize("hasAnyRole('STAFF', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<StaffScheduleResponse>> addBreak(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody BreakTimeRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(scheduleService.addBreak(scheduleId, request)));
    }

    // DELETE /staff/schedule/{scheduleId}/breaks/{breakId}
    @Operation(summary = "Remove a break time")
    @DeleteMapping("/{scheduleId}/breaks/{breakId}")
    @PreAuthorize("hasAnyRole('STAFF', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<StaffScheduleResponse>> removeBreak(
            @PathVariable UUID scheduleId,
            @PathVariable UUID breakId) {
        return ResponseEntity.ok(ApiResponse.ok(
                scheduleService.removeBreak(scheduleId, breakId)));
    }

    // DELETE /staff/schedule/{scheduleId}
    @Operation(summary = "Delete a staff's day schedule (reverts to shop hours)")
    @DeleteMapping("/{scheduleId}")
    @PreAuthorize("hasAnyRole('STAFF', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(@PathVariable UUID scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.ok(ApiResponse.ok("Schedule deleted. Shop hours will be used.", null));
    }
}
