package com.trimlink.module.booking.service;

import com.trimlink.common.dto.PageResponse;
import com.trimlink.common.exception.BusinessException;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.booking.dto.CreateReviewRequest;
import com.trimlink.module.booking.dto.ReviewResponse;
import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.booking.entity.Review;
import com.trimlink.module.booking.repository.AppointmentRepository;
import com.trimlink.module.booking.repository.ReviewRepository;
import com.trimlink.module.user.entity.StaffProfile;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.StaffProfileRepository;
import com.trimlink.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final StaffProfileRepository staffProfileRepository;

    @Transactional
    public ReviewResponse createReview(UUID appointmentId, UUID reviewerId, CreateReviewRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        if (!appointment.getCustomer().getId().equals(reviewerId)) {
            throw new AccessDeniedException("You can only review your own completed appointments.");
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new BusinessException("Only completed appointments can be reviewed.");
        }
        if (reviewRepository.existsByAppointmentId(appointmentId)) {
            throw new BusinessException("This appointment has already been reviewed.");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", reviewerId));
        StaffProfile staff = staffProfileRepository.findById(appointment.getStaff().getId())
                .orElseThrow(() -> new ResourceNotFoundException("StaffProfile", "id", appointment.getStaff().getId()));

        Review review = Review.builder()
                .appointment(appointment)
                .reviewer(reviewer)
                .staffProfile(staff)
                .rating(request.getRating().setScale(1, java.math.RoundingMode.HALF_UP))
                .comment(request.getComment())
                .build();

        review = reviewRepository.save(review);
        refreshStaffRatingSummary(staff);

        log.info("Created review {} for appointment={} staff={} reviewer={}",
                review.getId(), appointmentId, staff.getId(), reviewerId);
        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public ReviewResponse getReview(UUID reviewId) {
        return toResponse(reviewRepository.findByIdAndDeletedFalse(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listStaffReviews(UUID staffId, Pageable pageable) {
        staffProfileRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("StaffProfile", "id", staffId));

        var page = reviewRepository.findByStaffProfileId(staffId, PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort()));
        return PageResponse.from(page.map(this::toResponse));
    }

    private void refreshStaffRatingSummary(StaffProfile staff) {
        BigDecimal average = reviewRepository.calculateAverageRating(staff.getId());
        long total = reviewRepository.countByStaffProfileId(staff.getId());
        staff.updateRatingSummary(
                average != null ? average.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO,
                total
        );
        staffProfileRepository.save(staff);
    }

    private ReviewResponse toResponse(Review review) {
        return ReviewResponse.builder()
                .reviewId(review.getId())
                .appointmentId(review.getAppointment().getId())
                .staffId(review.getStaffProfile().getId())
                .staffName(review.getStaffProfile().getUser().getFirstName() + " "
                        + review.getStaffProfile().getUser().getLastName())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getFirstName() + " " + review.getReviewer().getLastName())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
