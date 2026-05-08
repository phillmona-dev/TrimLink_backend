package com.trimlink.module.user.repository;

import com.trimlink.module.user.entity.BarberServiceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BarberServiceAssignmentRepository extends JpaRepository<BarberServiceAssignment, UUID> {

    List<BarberServiceAssignment> findByBarberProfileIdAndActiveTrueAndDeletedFalseOrderByCreatedAtAsc(UUID barberProfileId);

    List<BarberServiceAssignment> findByBarberProfileIdAndDeletedFalseOrderByCreatedAtAsc(UUID barberProfileId);

    Optional<BarberServiceAssignment> findByIdAndDeletedFalse(UUID id);

    Optional<BarberServiceAssignment> findByBarberProfileIdAndServiceId(UUID barberProfileId, UUID serviceId);
}
