package com.trimlink.module.user.repository;

import com.trimlink.module.user.entity.StaffSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffScheduleRepository extends JpaRepository<StaffSchedule, UUID> {

    /** Full week schedule for scheduling dashboard. */
    @Query("""
            SELECT s FROM StaffSchedule s
            LEFT JOIN FETCH s.breakTimes b
            WHERE s.staffProfile.id = :staffId
              AND s.deleted = false
            ORDER BY s.dayOfWeek
            """)
    List<StaffSchedule> findWeekSchedule(@Param("staffId") UUID staffId);

    /** Single day — used during slot generation. */
    @Query("""
            SELECT s FROM StaffSchedule s
            LEFT JOIN FETCH s.breakTimes
            WHERE s.staffProfile.id = :staffId
              AND s.dayOfWeek = :day
              AND s.deleted = false
            """)
    Optional<StaffSchedule> findByStaffAndDay(
            @Param("staffId") UUID staffId,
            @Param("day") DayOfWeek day);
}
