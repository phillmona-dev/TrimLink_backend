package com.trimlink.module.queue.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.module.queue.dto.JoinQueueRequest;
import com.trimlink.module.queue.dto.QueueEntryResponse;
import com.trimlink.module.queue.dto.QueueTicketResponse;
import com.trimlink.module.queue.service.QueueService;
import com.trimlink.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Queue", description = "Walk-in queue (waitlist) management")
@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    // POST /queue/join — customer joins the queue
    @Operation(summary = "Join walk-in queue")
    @PostMapping("/join")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<QueueTicketResponse>> joinQueue(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody JoinQueueRequest request) {

        QueueTicketResponse ticket = queueService.joinQueue(principal.getUserId(), request);
        return ResponseEntity.status(201).body(ApiResponse.created(ticket));
    }

    // GET /queue/ticket/{entryId} — customer checks their position + ETA
    @Operation(summary = "Get my queue ticket (position + ETA)")
    @GetMapping("/ticket/{entryId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<QueueTicketResponse>> getTicket(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID entryId) {

        return ResponseEntity.ok(ApiResponse.ok(
                queueService.getMyTicket(principal.getUserId(), principal.getRole(), entryId)));
    }

    // GET /queue/barber/{barberId} — barber sees their full active queue
    @Operation(summary = "Get active queue for a barber")
    @GetMapping("/barber/{barberId}")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<QueueEntryResponse>>> getBarberQueue(
            @PathVariable UUID barberId) {

        return ResponseEntity.ok(ApiResponse.ok(queueService.getQueueForBarber(barberId)));
    }

    // GET /queue/shop/{shopId} — shop owner sees all barbers' queues
    @Operation(summary = "Get active queue for entire shop")
    @GetMapping("/shop/{shopId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<QueueEntryResponse>>> getShopQueue(
            @PathVariable UUID shopId) {

        return ResponseEntity.ok(ApiResponse.ok(queueService.getQueueForShop(shopId)));
    }

    // POST /queue/barber/{barberId}/call-next — barber calls the next customer
    @Operation(summary = "Call next customer in queue")
    @PostMapping("/barber/{barberId}/call-next")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<QueueTicketResponse>> callNext(
            @PathVariable UUID barberId) {

        return ResponseEntity.ok(ApiResponse.ok(queueService.callNext(barberId)));
    }

    // PATCH /queue/{entryId}/start-service — barber starts the service
    @Operation(summary = "Mark service as started for a queue entry")
    @PatchMapping("/{entryId}/start-service")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<QueueTicketResponse>> startService(
            @PathVariable UUID entryId) {

        return ResponseEntity.ok(ApiResponse.ok(queueService.startService(entryId)));
    }

    // PATCH /queue/{entryId}/complete — barber completes service, queue auto-advances
    @Operation(summary = "Complete service for queue entry (advances queue)")
    @PatchMapping("/{entryId}/complete")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<QueueTicketResponse>> completeService(
            @PathVariable UUID entryId) {

        return ResponseEntity.ok(ApiResponse.ok(queueService.completeService(entryId)));
    }

    // DELETE /queue/{entryId} — customer leaves the queue
    @Operation(summary = "Leave the queue")
    @DeleteMapping("/{entryId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> leaveQueue(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID entryId) {

        queueService.cancelEntry(principal.getUserId(), principal.getRole(), entryId);
        return ResponseEntity.ok(ApiResponse.ok("Left the queue successfully", null));
    }

    // PATCH /queue/{entryId}/skip — barber skips a non-responding customer
    @Operation(summary = "Skip a customer who did not respond")
    @PatchMapping("/{entryId}/skip")
    @PreAuthorize("hasAnyRole('BARBER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> skipCustomer(
            @PathVariable UUID entryId) {

        queueService.skipEntry(entryId);
        return ResponseEntity.ok(ApiResponse.ok("Customer skipped", null));
    }
}
