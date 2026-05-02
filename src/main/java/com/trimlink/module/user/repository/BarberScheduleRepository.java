package com.trimlink.module.user.repository;

import com.trimlink.module.user.entity.BarberSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BarberScheduleRepository extends JpaRepository<BarberSchedule, UUID> {

    /** Full week schedule for scheduling dashboard. */
    @Query("""
            SELECT s FROM BarberSchedule s
            LEFT JOIN FETCH s.breakTimes b
            WHERE s.barberProfile.id = :barberId
              AND s.deleted = false
            ORDER BY s.dayOfWeek
            """)
    List<BarberSchedule> findWeekSchedule(@Param("barberId") UUID barberId);

    /** Single day — used during slot generation. */
    @Query("""
            SELECT s FROM BarberSchedule s
            LEFT JOIN FETCH s.breakTimes
            WHERE s.barberProfile.id = :barberId
              AND s.dayOfWeek = :day
              AND s.deleted = false
            """)
    Optional<BarberSchedule> findByBarberAndDay(
            @Param("barberId") UUID barberId,
            @Param("day") DayOfWeek day);
}
