package com.trimlink.module.queue.repository;

import com.trimlink.module.queue.entity.QueueEntry;
import com.trimlink.module.queue.entity.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {

    /**
     * Full active queue for a barber ordered by joinedAt (FIFO).
     * JOIN FETCH prevents N+1 when rendering the queue dashboard.
     */
    @Query("""
            SELECT q FROM QueueEntry q
            JOIN FETCH q.customer c
            JOIN FETCH q.service s
            WHERE q.barber.id = :barberId
              AND q.status IN ('WAITING', 'CALLED', 'IN_SERVICE')
              AND q.deleted = false
            ORDER BY q.joinedAt ASC
            """)
    List<QueueEntry> findActiveQueueByBarber(@Param("barberId") UUID barberId);

    /**
     * All WAITING entries ahead of a given joinedAt timestamp for a barber.
     * Used to calculate queue position and ETA.
     */
    @Query("""
            SELECT q FROM QueueEntry q
            JOIN FETCH q.service s
            WHERE q.barber.id = :barberId
              AND q.status = 'WAITING'
              AND q.joinedAt < :myJoinedAt
              AND q.deleted = false
            ORDER BY q.joinedAt ASC
            """)
    List<QueueEntry> findEntriesAheadOf(
            @Param("barberId") UUID barberId,
            @Param("myJoinedAt") LocalDateTime myJoinedAt
    );

    /**
     * Currently active entry for the barber (IN_SERVICE or CALLED).
     */
    @Query("""
            SELECT q FROM QueueEntry q
            WHERE q.barber.id = :barberId
              AND q.status IN ('IN_SERVICE', 'CALLED')
              AND q.deleted = false
            """)
    Optional<QueueEntry> findCurrentEntry(@Param("barberId") UUID barberId);

    /**
     * Check if customer is already in the queue to prevent duplicate entries.
     */
    boolean existsByCustomerIdAndBarberIdAndStatusIn(
            UUID customerId, UUID barberId, List<QueueStatus> statuses);

    /**
     * Shop-level queue view (all barbers).
     */
    @Query("""
            SELECT q FROM QueueEntry q
            JOIN FETCH q.customer c
            JOIN FETCH q.barber b
            JOIN FETCH b.user u
            JOIN FETCH q.service s
            WHERE q.shop.id = :shopId
              AND q.status IN ('WAITING', 'CALLED', 'IN_SERVICE')
              AND q.deleted = false
            ORDER BY q.joinedAt ASC
            """)
    List<QueueEntry> findActiveQueueByShop(@Param("shopId") UUID shopId);

    /**
     * Count of completed services per barber today - for admin metrics.
     */
    @Query("""
            SELECT COUNT(q) FROM QueueEntry q
            WHERE q.barber.id = :barberId
              AND q.status = 'COMPLETED'
              AND q.serviceEndedAt >= :since
            """)
    long countCompletedSince(
            @Param("barberId") UUID barberId,
            @Param("since") LocalDateTime since
    );

    long countByStatusInAndDeletedFalse(List<QueueStatus> statuses);

    long countByBarberIdAndStatusInAndDeletedFalse(UUID barberId, List<QueueStatus> statuses);

    @Query("""
            SELECT COUNT(q) FROM QueueEntry q
            WHERE q.shop.id = :shopId
              AND q.status IN :statuses
              AND q.deleted = false
            """)
    long countByShopIdAndStatusInAndDeletedFalse(
            @Param("shopId") UUID shopId,
            @Param("statuses") List<QueueStatus> statuses
    );
}
