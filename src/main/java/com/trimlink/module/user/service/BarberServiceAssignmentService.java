package com.trimlink.module.user.service;

import com.trimlink.common.exception.BusinessException;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.service.repository.ServiceRepository;
import com.trimlink.module.user.dto.BarberServiceAssignmentRequest;
import com.trimlink.module.user.dto.BarberServiceAssignmentResponse;
import com.trimlink.module.user.dto.UpsertBarberServicesRequest;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.entity.BarberServiceAssignment;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.repository.BarberProfileRepository;
import com.trimlink.module.user.repository.BarberServiceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class BarberServiceAssignmentService {

    private final BarberProfileRepository barberProfileRepository;
    private final BarberServiceAssignmentRepository assignmentRepository;
    private final ServiceRepository serviceRepository;

    @Transactional(readOnly = true)
    public List<BarberServiceAssignmentResponse> listAssignments(UUID barberId) {
        findBarber(barberId);
        return assignmentRepository.findByBarberProfileIdAndActiveTrueAndDeletedFalseOrderByCreatedAtAsc(barberId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<BarberServiceAssignmentResponse> upsertAssignments(UUID barberId,
                                                                   UUID requesterId,
                                                                   String requesterRole,
                                                                   UpsertBarberServicesRequest request) {
        BarberProfile barber = findBarber(barberId);
        enforceAccess(barber, requesterId, requesterRole);
        validateNoDuplicates(request.getAssignments());

        List<BarberServiceAssignmentResponse> responses = request.getAssignments().stream()
                .map(assignmentRequest -> upsertSingle(barber, assignmentRequest))
                .map(this::toResponse)
                .toList();

        log.info("Upserted {} service assignments for barber={}", responses.size(), barberId);
        return responses;
    }

    @Transactional
    public BarberServiceAssignmentResponse deactivateAssignment(UUID barberId,
                                                                UUID assignmentId,
                                                                UUID requesterId,
                                                                String requesterRole) {
        BarberProfile barber = findBarber(barberId);
        enforceAccess(barber, requesterId, requesterRole);

        BarberServiceAssignment assignment = assignmentRepository.findByIdAndDeletedFalse(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("BarberServiceAssignment", "id", assignmentId));

        if (!assignment.getBarberProfile().getId().equals(barberId)) {
            throw new BusinessException("Assignment does not belong to the specified barber.");
        }

        assignment.setActive(false);
        assignment.softDelete();
        assignment = assignmentRepository.save(assignment);
        log.info("Deactivated service assignment {} for barber={}", assignmentId, barberId);
        return toResponse(assignment);
    }

    private BarberServiceAssignment upsertSingle(BarberProfile barber, BarberServiceAssignmentRequest request) {
        var service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", request.getServiceId()));

        if (!service.isActive()) {
            throw new BusinessException("Inactive services cannot be assigned to barbers.");
        }

        if (request.getCustomPrice() != null &&
            request.getCustomPrice().setScale(2, java.math.RoundingMode.HALF_UP)
                    .compareTo(service.getBasePrice().setScale(2, java.math.RoundingMode.HALF_UP)) < 0) {
            throw new BusinessException("Custom price cannot be lower than the base service price.");
        }

        BarberServiceAssignment assignment = assignmentRepository
                .findByBarberProfileIdAndServiceId(barber.getId(), service.getId())
                .orElseGet(() -> BarberServiceAssignment.builder()
                        .barberProfile(barber)
                        .service(service)
                        .build());

        assignment.setBarberProfile(barber);
        assignment.setService(service);
        assignment.setCustomPrice(request.getCustomPrice());
        assignment.setActive(true);
        assignment.setDeleted(false);
        assignment.setDeletedAt(null);

        return assignmentRepository.save(assignment);
    }

    private BarberProfile findBarber(UUID barberId) {
        return barberProfileRepository.findById(barberId)
                .orElseThrow(() -> new ResourceNotFoundException("BarberProfile", "id", barberId));
    }

    private void enforceAccess(BarberProfile barber, UUID requesterId, String requesterRole) {
        if (Role.ADMIN.name().equalsIgnoreCase(requesterRole) ||
            Role.OWNER.name().equalsIgnoreCase(requesterRole)) {
            return;
        }

        if (Role.BARBER.name().equalsIgnoreCase(requesterRole) &&
            barber.getUser().getId().equals(requesterId)) {
            return;
        }

        throw new AccessDeniedException("You are not allowed to manage services for this barber.");
    }

    private void validateNoDuplicates(List<BarberServiceAssignmentRequest> assignments) {
        Set<UUID> seen = new HashSet<>();
        for (BarberServiceAssignmentRequest assignment : assignments) {
            if (!seen.add(assignment.getServiceId())) {
                throw new BusinessException("Duplicate service IDs are not allowed in the same request.");
            }
        }
    }

    private BarberServiceAssignmentResponse toResponse(BarberServiceAssignment assignment) {
        BigDecimal effectivePrice = assignment.getCustomPrice() != null
                ? assignment.getCustomPrice()
                : assignment.getService().getBasePrice();

        return BarberServiceAssignmentResponse.builder()
                .assignmentId(assignment.getId())
                .barberId(assignment.getBarberProfile().getId())
                .serviceId(assignment.getService().getId())
                .serviceName(assignment.getService().getName())
                .serviceDescription(assignment.getService().getDescription())
                .durationMinutes(assignment.getService().getDurationMinutes())
                .basePrice(assignment.getService().getBasePrice())
                .customPrice(assignment.getCustomPrice())
                .effectivePrice(effectivePrice)
                .active(assignment.isActive() && !assignment.isDeleted())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }
}
