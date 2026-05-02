package com.trimlink.module.shop.repository;

import com.trimlink.module.shop.entity.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkingHoursRepository extends JpaRepository<WorkingHours, UUID> {
    Optional<WorkingHours> findByShopIdAndDayOfWeek(UUID shopId, DayOfWeek dayOfWeek);
    List<WorkingHours> findByShopIdOrderByDayOfWeek(UUID shopId);
}
