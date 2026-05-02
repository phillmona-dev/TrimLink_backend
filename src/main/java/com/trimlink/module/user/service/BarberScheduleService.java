package com.trimlink.module.user.service;

import com.trimlink.common.exception.BusinessException;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.user.dto.BarberScheduleRequest;
import com.trimlink.module.user.dto.BarberScheduleResponse;
import com.trimlink.module.user.dto.BreakTimeRequest;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.entity.BarberSchedule;
import com.trimlink.module.user.entity.BreakTime;
import com.trimlink.module.user.repository.BarberProfileRepository;
import com.trimlink.module.user.repository.BarberScheduleRepository;
import com.trimlink.module.user.repository.BreakTimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages a barber's personal working schedule and break times.
 *
 * Schedule hierarchy during slot generation:
 *   1. If BarberSchedule exists for that day → use it (can override shop hours)
 *   2. If BarberSchedule.dayOff = true → no slots
 *   3. If no BarberSchedule → fall back to ShopWorkingHours
 *   4. Within the schedule, slot overlap with any BreakTime → mark unavailable
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarberScheduleService {

    private final BarberScheduleRepository scheduleRepository;
    private final BreakTimeRepository      breakTimeRepository;
    private final BarberProfileRepository  barberProfileRepository;

    // ─── Get Week Schedule ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BarberScheduleResponse> getWeekSchedule(UUID barberId) {
        return scheduleRepository.findWeekSchedule(barberId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BarberScheduleResponse getDaySchedule(UUID barberId, DayOfWeek day) {
        return scheduleRepository.findByBarberAndDay(barberId, day)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No schedule set for " + day + " — falls back to shop hours."));
    }

    // ─── Upsert Day Schedule ───────────────────────────────────────────────

    @Transactional
    public BarberScheduleResponse upsertSchedule(UUID barberId, BarberScheduleRequest req) {
        BarberProfile barber = barberProfileRepository.findById(barberId)
                .orElseThrow(() -> new ResourceNotFoundException("BarberProfile", "id", barberId));

        // Validate times
        if (!req.isDayOff() && req.getStartTime().isAfter(req.getEndTime())) {
            throw new BusinessException("Start time must be before end time.");
        }

        BarberSchedule schedule = scheduleRepository
                .findByBarberAndDay(barberId, req.getDayOfWeek())
                .orElseGet(() -> BarberSchedule.builder()
                        .barberProfile(barber)
                        .dayOfWeek(req.getDayOfWeek())
                        .build());

        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setDayOff(req.isDayOff());

        schedule = scheduleRepository.save(schedule);
        log.info("Saved schedule for barber={} day={}", barberId, req.getDayOfWeek());
        return toResponse(schedule);
    }

    // ─── Break Time CRUD ───────────────────────────────────────────────────

    @Transactional
    public BarberScheduleResponse addBreak(UUID scheduleId, BreakTimeRequest req) {
        BarberSchedule schedule = findSchedule(scheduleId);

        // Validate break falls within working hours
        if (req.getStartTime().isBefore(schedule.getStartTime()) ||
            req.getEndTime().isAfter(schedule.getEndTime())) {
            throw new BusinessException("Break time must be within working hours ("
                    + schedule.getStartTime() + " – " + schedule.getEndTime() + ").");
        }
        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new BusinessException("Break start must be before break end.");
        }

        // Check overlap with existing breaks
        boolean overlaps = schedule.getBreakTimes().stream().anyMatch(b ->
                b.getStartTime().isBefore(req.getEndTime()) &&
                b.getEndTime().isAfter(req.getStartTime()));
        if (overlaps) {
            throw new BusinessException("Break overlaps with an existing break.");
        }

        BreakTime brk = BreakTime.builder()
                .barberSchedule(schedule)
                .label(req.getLabel())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .build();

        schedule.getBreakTimes().add(brk);
        return toResponse(scheduleRepository.save(schedule));
    }

    @Transactional
    public BarberScheduleResponse removeBreak(UUID scheduleId, UUID breakId) {
        BarberSchedule schedule = findSchedule(scheduleId);
        schedule.getBreakTimes().removeIf(b -> b.getId().equals(breakId));
        return toResponse(scheduleRepository.save(schedule));
    }

    // ─── Delete Day Schedule ───────────────────────────────────────────────

    @Transactional
    public void deleteSchedule(UUID scheduleId) {
        BarberSchedule schedule = findSchedule(scheduleId);
        schedule.softDelete();
        scheduleRepository.save(schedule);
    }

    // ─── Mappers ───────────────────────────────────────────────────────────

    private BarberScheduleResponse toResponse(BarberSchedule s) {
        List<BarberScheduleResponse.BreakTimeDto> breaks = s.getBreakTimes().stream()
                .map(b -> BarberScheduleResponse.BreakTimeDto.builder()
                        .id(b.getId())
                        .label(b.getLabel())
                        .startTime(b.getStartTime())
                        .endTime(b.getEndTime())
                        .build())
                .collect(Collectors.toList());

        return BarberScheduleResponse.builder()
                .id(s.getId())
                .barberId(s.getBarberProfile().getId())
                .dayOfWeek(s.getDayOfWeek())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .dayOff(s.isDayOff())
                .breakTimes(breaks)
                .build();
    }

    private BarberSchedule findSchedule(UUID id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BarberSchedule", "id", id));
    }
}
