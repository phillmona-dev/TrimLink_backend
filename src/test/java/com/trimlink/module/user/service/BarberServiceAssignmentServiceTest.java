package com.trimlink.module.user.service;

import com.trimlink.common.exception.BusinessException;
import com.trimlink.module.service.entity.Service;
import com.trimlink.module.service.repository.ServiceRepository;
import com.trimlink.module.user.dto.BarberServiceAssignmentRequest;
import com.trimlink.module.user.dto.UpsertBarberServicesRequest;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.entity.BarberServiceAssignment;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.BarberProfileRepository;
import com.trimlink.module.user.repository.BarberServiceAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BarberServiceAssignmentService Unit Tests")
class BarberServiceAssignmentServiceTest {

    @Mock private BarberProfileRepository barberProfileRepository;
    @Mock private BarberServiceAssignmentRepository assignmentRepository;
    @Mock private ServiceRepository serviceRepository;

    @InjectMocks
    private BarberServiceAssignmentService assignmentService;

    private UUID barberId;
    private UUID barberUserId;
    private UUID serviceId;
    private BarberProfile barberProfile;
    private Service haircutService;

    @BeforeEach
    void setUp() {
        barberId = UUID.randomUUID();
        barberUserId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        User barberUser = User.builder()
                .phoneNumber("+251911111111")
                .firstName("Dawit")
                .lastName("Haile")
                .role(Role.BARBER)
                .build();
        barberUser.setId(barberUserId);

        barberProfile = BarberProfile.builder()
                .user(barberUser)
                .build();
        barberProfile.setId(barberId);

        haircutService = Service.builder()
                .name("Haircut")
                .description("Classic haircut")
                .basePrice(new BigDecimal("150.00"))
                .durationMinutes(30)
                .active(true)
                .build();
        haircutService.setId(serviceId);
    }

    @Test
    @DisplayName("Barber can assign a service to their own profile")
    void upsertAssignments_barberOwnProfile_success() {
        var request = new UpsertBarberServicesRequest();
        var assignmentRequest = new BarberServiceAssignmentRequest();
        assignmentRequest.setServiceId(serviceId);
        assignmentRequest.setCustomPrice(new BigDecimal("180.00"));
        request.setAssignments(List.of(assignmentRequest));

        when(barberProfileRepository.findById(barberId)).thenReturn(Optional.of(barberProfile));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(haircutService));
        when(assignmentRepository.findByBarberProfileIdAndServiceId(barberId, serviceId)).thenReturn(Optional.empty());
        when(assignmentRepository.save(any())).thenAnswer(invocation -> {
            BarberServiceAssignment saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        var response = assignmentService.upsertAssignments(barberId, barberUserId, "BARBER", request);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getServiceId()).isEqualTo(serviceId);
        assertThat(response.get(0).getCustomPrice()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("Rejects duplicate service IDs in one request")
    void upsertAssignments_duplicateServiceIds_rejected() {
        var request = new UpsertBarberServicesRequest();
        var first = new BarberServiceAssignmentRequest();
        first.setServiceId(serviceId);
        var second = new BarberServiceAssignmentRequest();
        second.setServiceId(serviceId);
        request.setAssignments(List.of(first, second));

        when(barberProfileRepository.findById(barberId)).thenReturn(Optional.of(barberProfile));

        assertThatThrownBy(() -> assignmentService.upsertAssignments(barberId, barberUserId, "BARBER", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicate service IDs");

        verify(serviceRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Rejects access when barber tries to manage another barber profile")
    void upsertAssignments_otherBarber_forbidden() {
        var request = new UpsertBarberServicesRequest();
        var assignmentRequest = new BarberServiceAssignmentRequest();
        assignmentRequest.setServiceId(serviceId);
        request.setAssignments(List.of(assignmentRequest));

        when(barberProfileRepository.findById(barberId)).thenReturn(Optional.of(barberProfile));

        assertThatThrownBy(() -> assignmentService.upsertAssignments(barberId, UUID.randomUUID(), "BARBER", request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not allowed");
    }
}
