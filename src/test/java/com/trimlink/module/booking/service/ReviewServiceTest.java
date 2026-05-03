package com.trimlink.module.booking.service;

import com.trimlink.common.exception.BusinessException;
import com.trimlink.module.booking.dto.CreateReviewRequest;
import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.booking.entity.Review;
import com.trimlink.module.booking.repository.AppointmentRepository;
import com.trimlink.module.booking.repository.ReviewRepository;
import com.trimlink.module.user.entity.StaffProfile;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.StaffProfileRepository;
import com.trimlink.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService Unit Tests")
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private StaffProfileRepository staffProfileRepository;

    @InjectMocks
    private ReviewService reviewService;

    private UUID appointmentId;
    private UUID customerId;
    private UUID staffId;
    private User customer;
    private StaffProfile staffProfile;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        appointmentId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        staffId = UUID.randomUUID();

        customer = User.builder()
                .phoneNumber("+251912123456")
                .firstName("Abel")
                .lastName("Teshome")
                .role(Role.CUSTOMER)
                .build();
        customer.setId(customerId);

        User staffUser = User.builder()
                .phoneNumber("+251911654321")
                .firstName("Dawit")
                .lastName("Haile")
                .role(Role.STAFF)
                .build();

        staffProfile = StaffProfile.builder()
                .user(staffUser)
                .build();
        staffProfile.setId(staffId);

        appointment = Appointment.builder()
                .customer(customer)
                .staff(staffProfile)
                .status(AppointmentStatus.COMPLETED)
                .priceCharged(new BigDecimal("150.00"))
                .build();
        appointment.setId(appointmentId);
    }

    @Test
    @DisplayName("Creates a review for completed appointment and refreshes staff rating")
    void createReview_success() {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRating(new BigDecimal("4.5"));
        request.setComment("Great cut");

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(reviewRepository.existsByAppointmentId(appointmentId)).thenReturn(false);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(staffProfileRepository.findById(staffId)).thenReturn(Optional.of(staffProfile));
        when(reviewRepository.save(any())).thenAnswer(invocation -> {
            Review saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(reviewRepository.calculateAverageRating(staffId)).thenReturn(new BigDecimal("4.5"));
        when(reviewRepository.countByStaffProfileId(staffId)).thenReturn(1L);
        when(staffProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = reviewService.createReview(appointmentId, customerId, request);

        assertThat(response.getAppointmentId()).isEqualTo(appointmentId);
        assertThat(response.getStaffId()).isEqualTo(staffId);
        assertThat(response.getRating()).isEqualByComparingTo("4.5");
        assertThat(staffProfile.getAverageRating()).isEqualByComparingTo("4.50");
        assertThat(staffProfile.getTotalReviews()).isEqualTo(1);
    }

    @Test
    @DisplayName("Rejects review when appointment is not completed")
    void createReview_rejectsNonCompletedAppointment() {
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRating(new BigDecimal("5.0"));

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> reviewService.createReview(appointmentId, customerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("completed appointments");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects review from a different customer")
    void createReview_rejectsDifferentCustomer() {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRating(new BigDecimal("5.0"));

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> reviewService.createReview(appointmentId, UUID.randomUUID(), request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("own completed appointments");
    }
}
