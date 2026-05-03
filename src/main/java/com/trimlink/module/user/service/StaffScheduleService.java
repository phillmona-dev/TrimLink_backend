package com.trimlink.module.user.service;

import com.trimlink.common.exception.BusinessException;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.user.dto.StaffScheduleRequest;
import com.trimlink.module.user.dto.StaffScheduleResponse;
import com.trimlink.module.user.dto.BreakTimeRequest;
import com.trimlink.module.user.entity.StaffProfile;
import com.trimlink.module.user.entity.StaffSchedule;
import com.trimlink.module.user.entity.BreakTime;
import com.trimlink.module.user.repository.StaffProfileRepository;
import com.trimlink.module.user.repository.StaffScheduleRepository;
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
 * Manages a staff's personal working schedule and break times.
 *
 * Schedule hierarchy during slot generation:
 *   1. If StaffSchedule exists for that day → use it (can override shop hours)
 *   2. If StaffSchedule.dayOff = true → no slots
 *   3. If no StaffSchedule → fall back to ShopWorkingHours
 *   4. Within the schedule, slot overlap with any BreakTime → mark unavailable
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffScheduleService {

    private final StaffScheduleRepository scheduleRepository;
    private final BreakTimeRepository      breakTimeRepository;
    private final StaffProfileRepository  staffProfileRepository;

    // ─── Get Week Schedule ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StaffScheduleResponse> getWeekSchedule(UUID staffId) {
        return scheduleRepository.findWeekSchedule(staffId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StaffScheduleResponse getDaySchedule(UUID staffId, DayOfWeek day) {
        return scheduleRepository.findByStaffAndDay(staffId, day)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No schedule set for " + day + " — falls back to shop hours."));
    }

    // ─── Upsert Day Schedule ───────────────────────────────────────────────

    @Transactional
    public StaffScheduleResponse upsertSchedule(UUID staffId, StaffScheduleRequest req) {
        StaffProfile staff = staffProfileRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("StaffProfile", "id", staffId));

        // Validate times
        if (!req.isDayOff() && req.getStartTime().isAfter(req.getEndTime())) {
            throw new BusinessException("Start time must be before end time.");
        }

        StaffSchedule schedule = scheduleRepository
                .findByStaffAndDay(staffId, req.getDayOfWeek())
                .orElseGet(() -> StaffSchedule.builder()
                        .staffProfile(staff)
                        .dayOfWeek(req.getDayOfWeek())
                        .build());

        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setDayOff(req.isDayOff());

        schedule = scheduleRepository.save(schedule);
        log.info("Saved schedule for staff={} day={}", staffId, req.getDayOfWeek());
        return toResponse(schedule);
    }

    // ─── Break Time CRUD ───────────────────────────────────────────────────

    @Transactional
    public StaffScheduleResponse addBreak(UUID scheduleId, BreakTimeRequest req) {
        StaffSchedule schedule = findSchedule(scheduleId);

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
                .staffSchedule(schedule)
                .label(req.getLabel())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .build();

        schedule.getBreakTimes().add(brk);
        return toResponse(scheduleRepository.save(schedule));
    }

    @Transactional
    public StaffScheduleResponse removeBreak(UUID scheduleId, UUID breakId) {
        StaffSchedule schedule = findSchedule(scheduleId);
        schedule.getBreakTimes().removeIf(b -> b.getId().equals(breakId));
        return toResponse(scheduleRepository.save(schedule));
    }

    // ─── Delete Day Schedule ───────────────────────────────────────────────

    @Transactional
    public void deleteSchedule(UUID scheduleId) {
        StaffSchedule schedule = findSchedule(scheduleId);
        schedule.softDelete();
        scheduleRepository.save(schedule);
    }

    // ─── Mappers ───────────────────────────────────────────────────────────

    private StaffScheduleResponse toResponse(StaffSchedule s) {
        List<StaffScheduleResponse.BreakTimeDto> breaks = s.getBreakTimes().stream()
                .map(b -> StaffScheduleResponse.BreakTimeDto.builder()
                        .id(b.getId())
                        .label(b.getLabel())
                        .startTime(b.getStartTime())
                        .endTime(b.getEndTime())
                        .build())
                .collect(Collectors.toList());

        return StaffScheduleResponse.builder()
                .id(s.getId())
                .staffId(s.getStaffProfile().getId())
                .dayOfWeek(s.getDayOfWeek())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .dayOff(s.isDayOff())
                .breakTimes(breaks)
                .build();
    }

    private StaffSchedule findSchedule(UUID id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StaffSchedule", "id", id));
    }
}
