package com.trimlink.module.shop.repository;

import com.trimlink.module.shop.entity.DailyWorkLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyWorkLogRepository extends JpaRepository<DailyWorkLog, UUID> {
    Optional<DailyWorkLog> findByStaffIdAndLogDate(UUID staffId, LocalDate logDate);
    List<DailyWorkLog> findByStaffIdAndLogDateBetween(UUID staffId, LocalDate start, LocalDate end);
}
