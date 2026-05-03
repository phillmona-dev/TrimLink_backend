package com.trimlink.module.user.repository;

import com.trimlink.module.user.entity.StaffProfile;
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
public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {

    Optional<StaffProfile> findByUserId(UUID userId);

    long countByDeletedFalse();

    @Query("""
            SELECT b FROM StaffProfile b
            JOIN FETCH b.user u
            WHERE b.shop.id = :shopId
              AND b.deleted = false
              AND b.available = true
            ORDER BY b.averageRating DESC
            """)
    List<StaffProfile> findByShopIdAndDeletedFalseAndAvailableTrueOrderByAverageRatingDesc(@Param("shopId") UUID shopId);

    Page<StaffProfile> findByShopIdAndDeletedFalse(UUID shopId, Pageable pageable);

    @Query(
            value = """
                    SELECT b FROM StaffProfile b
                    JOIN FETCH b.user u
                    LEFT JOIN FETCH b.shop s
                    WHERE b.deleted = false
                    """,
            countQuery = """
                    SELECT COUNT(b) FROM StaffProfile b
                    WHERE b.deleted = false
                    """
    )
    Page<StaffProfile> findAllActiveWithUser(Pageable pageable);
}
