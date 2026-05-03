package com.trimlink.module.user.entity;

import com.trimlink.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/**
 * A break (lunch, prayer, coffee) within a staff's schedule.
 * Slots that overlap a break are marked unavailable during slot generation.
 */
@Entity
@Table(name = "break_times")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreakTime extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_schedule_id", nullable = false)
    private StaffSchedule staffSchedule;

    @Column(name = "label", length = 100)
    private String label;           // e.g. "Lunch", "Zuhr Prayer"

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
