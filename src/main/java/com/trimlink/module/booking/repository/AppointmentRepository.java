package com.trimlink.module.booking.repository;

import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.booking.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    /**
     * Overlap detection: finds any active appointment for a barber that
     * intersects the requested [start, end) window.
     * Uses pessimistic write lock to prevent race conditions during concurrent booking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.barber.id = :barberId
              AND a.status NOT IN ('CANCELLED', 'NO_SHOW', 'REJECTED', 'COMPLETED')
              AND a.scheduledStart < :end
              AND a.scheduledEnd > :start
            """)
    List<Appointment> findOverlapping(
            @Param("barberId") UUID barberId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.shop.id = :shopId AND a.scheduledStart >= :start AND a.scheduledStart < :end AND a.ticketNumber IS NOT NULL")
    long countWithTicketNumber(@Param("shopId") UUID shopId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Customer's appointment history with JOIN FETCH to avoid N+1.
     */
    @Query(value = """
            SELECT a FROM Appointment a
            JOIN FETCH a.service s
            JOIN FETCH a.barber b
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
                   OR LOWER(a.barber.user.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(a.barber.user.lastName) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<Appointment> searchByCustomerId(
            @Param("customerId") UUID customerId,
            @Param("query") String query,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.service s
            JOIN FETCH a.barber b
            JOIN FETCH b.user u
            JOIN FETCH a.shop sh
            WHERE a.customer.id = :customerId
              AND a.deleted = false
            """)
    Page<Appointment> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);

    /**
     * Barber's schedule for a given day - used for dashboard + slot generation.
     */
    @Query("""
            SELECT a FROM Appointment a
            LEFT JOIN FETCH a.service
            LEFT JOIN FETCH a.customer
            WHERE a.barber.id = :barberId
              AND a.scheduledStart >= :dayStart
              AND a.scheduledStart < :dayEnd
              AND a.status NOT IN ('CANCELLED', 'NO_SHOW', 'REJECTED', 'COMPLETED')
              AND a.deleted = false
            ORDER BY a.scheduledStart
            """)
    List<Appointment> findBarberDaySchedule(
            @Param("barberId") UUID barberId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    /**
     * Revenue aggregation per shop - used by admin analytics.
     */
    @Query("""
            SELECT SUM(a.priceCharged) FROM Appointment a
            WHERE (:shopId IS NULL OR a.shop.id = :shopId)
              AND a.status = 'COMPLETED'
              AND a.scheduledStart BETWEEN :from AND :to
              AND a.deleted = false
            """)
    java.math.BigDecimal sumRevenueByShop(
            @Param("shopId") UUID shopId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(value = """
            SELECT a FROM Appointment a
            JOIN FETCH a.service s
            JOIN FETCH a.customer c
            WHERE a.barber.user.id = :userId
              AND (a.status = :status OR :status IS NULL)
              AND a.deleted = false
              AND (:search IS NULL OR :search = '' OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:dayStart IS NULL OR a.scheduledStart >= :dayStart)
              AND (:dayEnd IS NULL OR a.scheduledStart < :dayEnd)
            """,
            countQuery = """
            SELECT COUNT(a) FROM Appointment a
            WHERE a.barber.user.id = :userId
              AND (a.status = :status OR :status IS NULL)
              AND a.deleted = false
              AND (:search IS NULL OR :search = '' OR LOWER(a.customer.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(a.customer.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:dayStart IS NULL OR a.scheduledStart >= :dayStart)
              AND (:dayEnd IS NULL OR a.scheduledStart < :dayEnd)
            """)
    Page<Appointment> searchBarberAppointments(
            @Param("userId") UUID userId,
            @Param("status") AppointmentStatus status,
            @Param("search") String search,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            Pageable pageable);

    Page<Appointment> findByBarberIdAndStatusAndDeletedFalse(
            UUID barberId, AppointmentStatus status, Pageable pageable);

    Page<Appointment> findByShopIdAndDeletedFalse(UUID shopId, Pageable pageable);

    long countByDeletedFalseAndScheduledStartBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusAndDeletedFalse(AppointmentStatus status);

    boolean existsByBarberIdAndStatusAndDeletedFalse(UUID barberId, AppointmentStatus status);

    long countByBarberIdAndStatusAndDeletedFalse(UUID barberId, AppointmentStatus status);

    long countByBarberIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
            UUID barberId, AppointmentStatus status, LocalDateTime from, LocalDateTime to);

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

    @Query("SELECT DISTINCT a.customer.id FROM Appointment a WHERE a.shop.id = :shopId AND a.deleted = false")
    List<UUID> findUniqueCustomerIdsByShopId(@Param("shopId") UUID shopId);

    @Query("""
            SELECT a.shop.id, a.shop.name, SUM(a.priceCharged)
            FROM Appointment a
            WHERE a.status = 'COMPLETED' AND a.deleted = false
            GROUP BY a.shop.id, a.shop.name
            """)
    List<Object[]> sumRevenueGroupByShop();

    @Query("""
            SELECT b.id, u.firstName, u.lastName, SUM(a.priceCharged)
            FROM Appointment a
            JOIN a.barber b
            JOIN b.user u
            WHERE a.status = 'COMPLETED' AND a.deleted = false
            GROUP BY b.id, u.firstName, u.lastName
            """)
    List<Object[]> sumRevenueGroupByBarber();

    @Query(value = """
            SELECT a FROM Appointment a
            LEFT JOIN FETCH a.service svc
            LEFT JOIN FETCH a.customer c
            LEFT JOIN FETCH a.barber b
            LEFT JOIN FETCH b.user u
            WHERE a.shop.id = :shopId
              AND a.deleted = false
              AND (a.status = :status OR :status IS NULL)
              AND a.scheduledStart >= :from
              AND a.scheduledStart <= :to
              AND (:query IS NULL OR :query = ''
                   OR LOWER(COALESCE(c.firstName,'')) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(COALESCE(c.lastName,'')) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(COALESCE(u.firstName,'')) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(COALESCE(u.lastName,'')) LIKE LOWER(CONCAT('%', :query, '%')))
            """,
            countQuery = """
            SELECT COUNT(a) FROM Appointment a
            WHERE a.shop.id = :shopId
              AND a.deleted = false
              AND (a.status = :status OR :status IS NULL)
              AND a.scheduledStart >= :from
              AND a.scheduledStart <= :to
              AND (:query IS NULL OR :query = ''
                   OR LOWER(COALESCE(a.customer.firstName,'')) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(COALESCE(a.customer.lastName,'')) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(COALESCE(a.barber.user.firstName,'')) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(COALESCE(a.barber.user.lastName,'')) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<Appointment> findByShopIdWithFilters(
            @Param("shopId") UUID shopId,
            @Param("status") AppointmentStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("query") String query,
            Pageable pageable);
}
