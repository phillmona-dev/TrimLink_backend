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
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.BarberProfileRepository;
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
    private final BarberProfileRepository barberProfileRepository;

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
        BarberProfile barber = barberProfileRepository.findById(appointment.getBarber().getId())
                .orElseThrow(() -> new ResourceNotFoundException("BarberProfile", "id", appointment.getBarber().getId()));

        Review review = Review.builder()
                .appointment(appointment)
                .reviewer(reviewer)
                .barberProfile(barber)
                .rating(request.getRating().setScale(1, java.math.RoundingMode.HALF_UP))
                .comment(request.getComment())
                .build();

        review = reviewRepository.save(review);
        refreshBarberRatingSummary(barber);

        log.info("Created review {} for appointment={} barber={} reviewer={}",
                review.getId(), appointmentId, barber.getId(), reviewerId);
        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public ReviewResponse getReview(UUID reviewId) {
        return toResponse(reviewRepository.findByIdAndDeletedFalse(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listBarberReviews(UUID barberId, Pageable pageable) {
        barberProfileRepository.findById(barberId)
                .orElseThrow(() -> new ResourceNotFoundException("BarberProfile", "id", barberId));

        var page = reviewRepository.findByBarberProfileId(barberId, PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort()));
        return PageResponse.from(page.map(this::toResponse));
    }

    private void refreshBarberRatingSummary(BarberProfile barber) {
        BigDecimal average = reviewRepository.calculateAverageRating(barber.getId());
        long total = reviewRepository.countByBarberProfileId(barber.getId());
        barber.updateRatingSummary(
                average != null ? average.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO,
                total
        );
        barberProfileRepository.save(barber);
    }

    private ReviewResponse toResponse(Review review) {
        return ReviewResponse.builder()
                .reviewId(review.getId())
                .appointmentId(review.getAppointment().getId())
                .barberId(review.getBarberProfile().getId())
                .barberName(review.getBarberProfile().getUser().getFirstName() + " "
                        + review.getBarberProfile().getUser().getLastName())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getFirstName() + " " + review.getReviewer().getLastName())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
