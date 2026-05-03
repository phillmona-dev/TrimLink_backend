package com.trimlink.module.user.service;

import com.trimlink.common.exception.BusinessException;
import com.trimlink.module.service.entity.Service;
import com.trimlink.module.service.repository.ServiceRepository;
import com.trimlink.module.user.dto.StaffServiceAssignmentRequest;
import com.trimlink.module.user.dto.UpsertStaffServicesRequest;
import com.trimlink.module.user.entity.StaffProfile;
import com.trimlink.module.user.entity.StaffServiceAssignment;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.StaffProfileRepository;
import com.trimlink.module.user.repository.StaffServiceAssignmentRepository;
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
@DisplayName("StaffServiceAssignmentService Unit Tests")
class StaffServiceAssignmentServiceTest {

    @Mock private StaffProfileRepository staffProfileRepository;
    @Mock private StaffServiceAssignmentRepository assignmentRepository;
    @Mock private ServiceRepository serviceRepository;

    @InjectMocks
    private StaffServiceAssignmentService assignmentService;

    private UUID staffId;
    private UUID staffUserId;
    private UUID serviceId;
    private StaffProfile staffProfile;
    private Service haircutService;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        staffUserId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        User staffUser = User.builder()
                .phoneNumber("+251911111111")
                .firstName("Dawit")
                .lastName("Haile")
                .role(Role.STAFF)
                .build();
        staffUser.setId(staffUserId);

        staffProfile = StaffProfile.builder()
                .user(staffUser)
                .build();
        staffProfile.setId(staffId);

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
    @DisplayName("Staff can assign a service to their own profile")
    void upsertAssignments_staffOwnProfile_success() {
        var request = new UpsertStaffServicesRequest();
        var assignmentRequest = new StaffServiceAssignmentRequest();
        assignmentRequest.setServiceId(serviceId);
        assignmentRequest.setCustomPrice(new BigDecimal("180.00"));
        request.setAssignments(List.of(assignmentRequest));

        when(staffProfileRepository.findById(staffId)).thenReturn(Optional.of(staffProfile));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(haircutService));
        when(assignmentRepository.findByStaffProfileIdAndServiceId(staffId, serviceId)).thenReturn(Optional.empty());
        when(assignmentRepository.save(any())).thenAnswer(invocation -> {
            StaffServiceAssignment saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        var response = assignmentService.upsertAssignments(staffId, staffUserId, "STAFF", request);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getServiceId()).isEqualTo(serviceId);
        assertThat(response.get(0).getCustomPrice()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("Rejects duplicate service IDs in one request")
    void upsertAssignments_duplicateServiceIds_rejected() {
        var request = new UpsertStaffServicesRequest();
        var first = new StaffServiceAssignmentRequest();
        first.setServiceId(serviceId);
        var second = new StaffServiceAssignmentRequest();
        second.setServiceId(serviceId);
        request.setAssignments(List.of(first, second));

        when(staffProfileRepository.findById(staffId)).thenReturn(Optional.of(staffProfile));

        assertThatThrownBy(() -> assignmentService.upsertAssignments(staffId, staffUserId, "STAFF", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicate service IDs");

        verify(serviceRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Rejects access when staff tries to manage another staff profile")
    void upsertAssignments_otherStaff_forbidden() {
        var request = new UpsertStaffServicesRequest();
        var assignmentRequest = new StaffServiceAssignmentRequest();
        assignmentRequest.setServiceId(serviceId);
        request.setAssignments(List.of(assignmentRequest));

        when(staffProfileRepository.findById(staffId)).thenReturn(Optional.of(staffProfile));

        assertThatThrownBy(() -> assignmentService.upsertAssignments(staffId, UUID.randomUUID(), "STAFF", request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not allowed");
    }
}
