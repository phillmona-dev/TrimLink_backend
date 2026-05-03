package com.trimlink.module.booking.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.common.dto.PageResponse;
import com.trimlink.module.booking.dto.CreateReviewRequest;
import com.trimlink.module.booking.dto.ReviewResponse;
import com.trimlink.module.booking.service.ReviewService;
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

import java.util.UUID;

@Tag(name = "Reviews", description = "Customer reviews and staff rating APIs")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Create a review for a completed appointment")
    @PostMapping("/bookings/{appointmentId}/review")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(
                reviewService.createReview(appointmentId, principal.getUserId(), request)));
    }

    @Operation(summary = "Get a single review by ID")
    @GetMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReview(@PathVariable UUID reviewId) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getReview(reviewId)));
    }

    @Operation(summary = "List reviews for a staff")
    @GetMapping("/staffs/{staffId}/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> listStaffReviews(
            @PathVariable UUID staffId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listStaffReviews(staffId, pageable)));
    }
}
