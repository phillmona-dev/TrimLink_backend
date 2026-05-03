package com.trimlink.module.booking.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.common.dto.PageResponse;
import com.trimlink.module.booking.dto.AppointmentResponse;
import com.trimlink.module.booking.dto.CreateAppointmentRequest;
import com.trimlink.module.booking.dto.SlotAvailabilityRequest;
import com.trimlink.module.booking.dto.TimeSlotResponse;
import com.trimlink.module.booking.service.BookingService;
import com.trimlink.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Bookings", description = "Appointment booking and slot management")
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // POST /bookings — create appointment
    @Operation(summary = "Create a new appointment")
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> createAppointment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateAppointmentRequest request) {

        AppointmentResponse response = bookingService.createAppointment(
                principal.getUserId(), request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    // GET /bookings/slots — query available time slots
    @Operation(summary = "Get available time slots for a barber on a date")
    @GetMapping("/slots")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getSlots(
            @Valid SlotAvailabilityRequest request) {

        List<TimeSlotResponse> slots = bookingService.getAvailableSlots(request);
        return ResponseEntity.ok(ApiResponse.ok(slots));
    }

    // GET /bookings/{id}
    @Operation(summary = "Get appointment by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.getByIdForUser(id, principal.getUserId(), principal.getRole())));
    }

    @Operation(summary = "Get current customer's appointments")
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> getMy(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime since,
            @PageableDefault(size = 10, sort = "scheduledStart", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                bookingService.getCustomerAppointments(principal.getUserId(), query, since, pageable))));
    }

    @Operation(summary = "Get my appointments (as barber/owner)")
    @GetMapping("/barber")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER')")
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> getBarberAppointments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false, defaultValue = "PENDING") com.trimlink.module.booking.entity.AppointmentStatus status,
            @PageableDefault(size = 20, sort = "scheduledStart") Pageable pageable) {

        PageResponse<AppointmentResponse> page = PageResponse.from(
                bookingService.getBarberAppointments(principal.getUserId(), status, pageable));
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    // PATCH /bookings/{id}/confirm
    @Operation(summary = "Confirm an appointment (barber/owner)")
    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.confirmAppointment(id)));
    }

    // PATCH /bookings/{id}/start
    @Operation(summary = "Start appointment service (barber)")
    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.startAppointment(id)));
    }

    // PATCH /bookings/{id}/complete
    @Operation(summary = "Mark appointment as completed (barber)")
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.completeAppointment(id)));
    }

    // PATCH /bookings/{id}/reject
    @Operation(summary = "Reject an appointment (barber/owner)")
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reject(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Rejected by barber") String reason) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.rejectAppointment(id, reason)));
    }

    // PATCH /bookings/{id}/reschedule
    @Operation(summary = "Request appointment reschedule (barber/owner)")
    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reschedule(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Please reschedule") String reason) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.requestRescheduleAppointment(id, reason)));
    }

    // PATCH /bookings/{id}/cancel
    @Operation(summary = "Cancel an appointment")
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Cancelled by user") String reason) {

        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.cancelAppointmentForUser(id, principal.getUserId(), principal.getRole(), reason)));
    }
}
