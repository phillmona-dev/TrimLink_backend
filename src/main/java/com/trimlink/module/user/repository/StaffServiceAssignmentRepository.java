package com.trimlink.module.user.repository;

import com.trimlink.module.user.entity.StaffServiceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffServiceAssignmentRepository extends JpaRepository<StaffServiceAssignment, UUID> {

    List<StaffServiceAssignment> findByStaffProfileIdAndActiveTrueAndDeletedFalseOrderByCreatedAtAsc(UUID staffProfileId);

    Optional<StaffServiceAssignment> findByIdAndDeletedFalse(UUID id);

    Optional<StaffServiceAssignment> findByStaffProfileIdAndServiceId(UUID staffProfileId, UUID serviceId);
}
