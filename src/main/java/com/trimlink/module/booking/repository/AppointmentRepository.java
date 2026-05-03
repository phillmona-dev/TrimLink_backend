package com.trimlink.module.booking.repository;

import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.booking.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /**
     * Overlap detection: finds any active appointment for a staff that
     * intersects the requested [start, end) window.
     * Uses pessimistic write lock to prevent race conditions during concurrent booking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.staff.id = :staffId
              AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
              AND a.scheduledStart < :end
              AND a.scheduledEnd > :start
            """)
    List<Appointment> findOverlapping(
            @Param("staffId") UUID staffId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Customer's appointment history with JOIN FETCH to avoid N+1.
     */
    @Query(value = """
            SELECT a FROM Appointment a
            JOIN FETCH a.service s
            JOIN FETCH a.staff b
            JOIN FETCH b.user u
            JOIN FETCH a.shop sh
            WHERE a.customer.id = :customerId
              AND a.deleted = false
              AND a.scheduledStart >= :since
              AND (LOWER(sh.name) LIKE LOWER(CONCAT('%', :query, '%')) 
                   OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')))
            """,
            countQuery = """
            SELECT COUNT(a) FROM Appointment a
            WHERE a.customer.id = :customerId
              AND a.deleted = false
              AND a.scheduledStart >= :since
              AND (LOWER(a.shop.name) LIKE LOWER(CONCAT('%', :query, '%')) 
                   OR LOWER(a.service.name) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(a.staff.user.firstName) LIKE LOWER(CONCAT('%', :query, '%')) 
                   OR LOWER(a.staff.user.lastName) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<Appointment> searchByCustomerId(
            @Param("customerId") UUID customerId,
            @Param("query") String query,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.service s
            JOIN FETCH a.staff b
            JOIN FETCH b.user u
            JOIN FETCH a.shop sh
            WHERE a.customer.id = :customerId
              AND a.deleted = false
            """)
    Page<Appointment> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);

    /**
     * Staff's schedule for a given day - used for dashboard + slot generation.
     */
    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.service
            JOIN FETCH a.customer c
            WHERE a.staff.id = :staffId
              AND a.scheduledStart >= :dayStart
              AND a.scheduledStart < :dayEnd
              AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
              AND a.deleted = false
            ORDER BY a.scheduledStart
            """)
    List<Appointment> findStaffDaySchedule(
            @Param("staffId") UUID staffId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    /**
     * Revenue aggregation per shop - used by admin analytics.
     */
    @Query("""
            SELECT SUM(a.priceCharged) FROM Appointment a
            WHERE a.shop.id = :shopId
              AND a.status = 'COMPLETED'
              AND a.scheduledStart BETWEEN :from AND :to
            """)
    java.math.BigDecimal sumRevenueByShop(
            @Param("shopId") UUID shopId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.staff.user.id = :userId
              AND a.status = :status
              AND a.deleted = false
            """)
    Page<Appointment> findByStaffUserIdAndStatus(@Param("userId") UUID userId, @Param("status") AppointmentStatus status, Pageable pageable);

    Page<Appointment> findByStaffIdAndStatusAndDeletedFalse(
            UUID staffId, AppointmentStatus status, Pageable pageable);

    Page<Appointment> findByShopIdAndDeletedFalse(UUID shopId, Pageable pageable);

    long countByDeletedFalseAndScheduledStartBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusAndDeletedFalse(AppointmentStatus status);

    boolean existsByStaffIdAndStatusAndDeletedFalse(UUID staffId, AppointmentStatus status);

    long countByStaffIdAndStatusAndDeletedFalse(UUID staffId, AppointmentStatus status);

    long countByStaffIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
            UUID staffId, AppointmentStatus status, LocalDateTime from, LocalDateTime to);

    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.shop.id = :shopId
              AND a.status = :status
              AND a.scheduledStart BETWEEN :from AND :to
              AND a.deleted = false
            """)
    long countByShopIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
            @Param("shopId") UUID shopId,
            @Param("status") AppointmentStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.shop.id = :shopId
              AND a.scheduledStart BETWEEN :from AND :to
              AND a.deleted = false
            """)
    long countByShopIdAndScheduledStartBetweenAndDeletedFalse(
            @Param("shopId") UUID shopId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT COUNT(DISTINCT a.customer.id) FROM Appointment a WHERE a.shop.id = :shopId AND a.deleted = false")
    long countUniqueCustomersByShopId(@Param("shopId") UUID shopId);
}
