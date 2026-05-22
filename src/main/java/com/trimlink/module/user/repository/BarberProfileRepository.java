package com.trimlink.module.user.repository;

import com.trimlink.module.user.entity.BarberProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BarberProfileRepository extends JpaRepository<BarberProfile, UUID> {

    Optional<BarberProfile> findByUserId(UUID userId);

    long countByDeletedFalse();

    @Query("""
            SELECT b FROM BarberProfile b
            JOIN FETCH b.user u
            WHERE b.shop.id = :shopId
              AND b.deleted = false
              AND b.available = true
            ORDER BY b.averageRating DESC
            """)
    List<BarberProfile> findByShopIdAndDeletedFalseAndAvailableTrueOrderByAverageRatingDesc(@Param("shopId") UUID shopId);

    Page<BarberProfile> findByShopIdAndDeletedFalse(UUID shopId, Pageable pageable);

    @Query(
            value = """
                    SELECT b FROM BarberProfile b
                    JOIN FETCH b.user u
                    LEFT JOIN FETCH b.shop s
                    WHERE b.deleted = false
                    AND (:q IS NULL OR :q = '' OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :q, '%')) OR (s IS NOT NULL AND LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%'))))
                    """,
            countQuery = """
                    SELECT COUNT(b) FROM BarberProfile b
                    JOIN b.user u
                    LEFT JOIN b.shop s
                    WHERE b.deleted = false
                    AND (:q IS NULL OR :q = '' OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :q, '%')) OR (s IS NOT NULL AND LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%'))))
                    """
    )
    Page<BarberProfile> searchActiveWithUser(@Param("q") String q, Pageable pageable);
}
