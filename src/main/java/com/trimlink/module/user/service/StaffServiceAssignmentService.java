package com.trimlink.module.user.service;

import com.trimlink.common.exception.BusinessException;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.service.repository.ServiceRepository;
import com.trimlink.module.user.dto.StaffServiceAssignmentRequest;
import com.trimlink.module.user.dto.StaffServiceAssignmentResponse;
import com.trimlink.module.user.dto.UpsertStaffServicesRequest;
import com.trimlink.module.user.entity.StaffProfile;
import com.trimlink.module.user.entity.StaffServiceAssignment;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.repository.StaffProfileRepository;
import com.trimlink.module.user.repository.StaffServiceAssignmentRepository;
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
public class StaffServiceAssignmentService {

    private final StaffProfileRepository staffProfileRepository;
    private final StaffServiceAssignmentRepository assignmentRepository;
    private final ServiceRepository serviceRepository;

    @Transactional(readOnly = true)
    public List<StaffServiceAssignmentResponse> listAssignments(UUID staffId) {
        findStaff(staffId);
        return assignmentRepository.findByStaffProfileIdAndActiveTrueAndDeletedFalseOrderByCreatedAtAsc(staffId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<StaffServiceAssignmentResponse> upsertAssignments(UUID staffId,
                                                                   UUID requesterId,
                                                                   String requesterRole,
                                                                   UpsertStaffServicesRequest request) {
        StaffProfile staff = findStaff(staffId);
        enforceAccess(staff, requesterId, requesterRole);
        validateNoDuplicates(request.getAssignments());

        List<StaffServiceAssignmentResponse> responses = request.getAssignments().stream()
                .map(assignmentRequest -> upsertSingle(staff, assignmentRequest))
                .map(this::toResponse)
                .toList();

        log.info("Upserted {} service assignments for staff={}", responses.size(), staffId);
        return responses;
    }

    @Transactional
    public StaffServiceAssignmentResponse deactivateAssignment(UUID staffId,
                                                                UUID assignmentId,
                                                                UUID requesterId,
                                                                String requesterRole) {
        StaffProfile staff = findStaff(staffId);
        enforceAccess(staff, requesterId, requesterRole);

        StaffServiceAssignment assignment = assignmentRepository.findByIdAndDeletedFalse(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("StaffServiceAssignment", "id", assignmentId));

        if (!assignment.getStaffProfile().getId().equals(staffId)) {
            throw new BusinessException("Assignment does not belong to the specified staff.");
        }

        assignment.setActive(false);
        assignment.softDelete();
        assignment = assignmentRepository.save(assignment);
        log.info("Deactivated service assignment {} for staff={}", assignmentId, staffId);
        return toResponse(assignment);
    }

    private StaffServiceAssignment upsertSingle(StaffProfile staff, StaffServiceAssignmentRequest request) {
        var service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", request.getServiceId()));

        if (!service.isActive()) {
            throw new BusinessException("Inactive services cannot be assigned to staffs.");
        }

        if (request.getCustomPrice() != null &&
            request.getCustomPrice().setScale(2, java.math.RoundingMode.HALF_UP)
                    .compareTo(service.getBasePrice().setScale(2, java.math.RoundingMode.HALF_UP)) < 0) {
            throw new BusinessException("Custom price cannot be lower than the base service price.");
        }

        StaffServiceAssignment assignment = assignmentRepository
                .findByStaffProfileIdAndServiceId(staff.getId(), service.getId())
                .orElseGet(() -> StaffServiceAssignment.builder()
                        .staffProfile(staff)
                        .service(service)
                        .build());

        assignment.setStaffProfile(staff);
        assignment.setService(service);
        assignment.setCustomPrice(request.getCustomPrice());
        assignment.setActive(true);
        assignment.setDeleted(false);
        assignment.setDeletedAt(null);

        return assignmentRepository.save(assignment);
    }

    private StaffProfile findStaff(UUID staffId) {
        return staffProfileRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("StaffProfile", "id", staffId));
    }

    private void enforceAccess(StaffProfile staff, UUID requesterId, String requesterRole) {
        if (Role.ADMIN.name().equalsIgnoreCase(requesterRole) ||
            Role.OWNER.name().equalsIgnoreCase(requesterRole)) {
            return;
        }

        if (Role.STAFF.name().equalsIgnoreCase(requesterRole) &&
            staff.getUser().getId().equals(requesterId)) {
            return;
        }

        throw new AccessDeniedException("You are not allowed to manage services for this staff.");
    }

    private void validateNoDuplicates(List<StaffServiceAssignmentRequest> assignments) {
        Set<UUID> seen = new HashSet<>();
        for (StaffServiceAssignmentRequest assignment : assignments) {
            if (!seen.add(assignment.getServiceId())) {
                throw new BusinessException("Duplicate service IDs are not allowed in the same request.");
            }
        }
    }

    private StaffServiceAssignmentResponse toResponse(StaffServiceAssignment assignment) {
        BigDecimal effectivePrice = assignment.getCustomPrice() != null
                ? assignment.getCustomPrice()
                : assignment.getService().getBasePrice();

        return StaffServiceAssignmentResponse.builder()
                .assignmentId(assignment.getId())
                .staffId(assignment.getStaffProfile().getId())
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
