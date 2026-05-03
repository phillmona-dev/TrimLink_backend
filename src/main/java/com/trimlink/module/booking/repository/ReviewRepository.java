package com.trimlink.module.booking.repository;

import com.trimlink.module.booking.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByStaffProfileId(UUID staffId, Pageable pageable);

    Page<Review> findByStaffProfileShopId(UUID shopId, Pageable pageable);

    boolean existsByAppointmentId(UUID appointmentId);

    long countByStaffProfileId(UUID staffId);

    Optional<Review> findByIdAndDeletedFalse(UUID reviewId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.staffProfile.id = :staffId")
    BigDecimal calculateAverageRating(@Param("staffId") UUID staffId);
}
