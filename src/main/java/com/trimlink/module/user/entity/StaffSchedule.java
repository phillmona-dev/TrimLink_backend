package com.trimlink.module.user.entity;

import com.trimlink.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Staff's own working schedule for a given day.
 * Takes precedence over shop working hours for slot generation.
 * If no staff schedule exists for a day, falls back to shop hours.
 */
@Entity
@Table(name = "staff_schedules", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"staff_profile_id", "day_of_week"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffSchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_profile_id", nullable = false)
    private StaffProfile staffProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 15)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** If true, staff is off this day regardless of shop hours. */
    @Column(name = "day_off", nullable = false)
    @Builder.Default
    private boolean dayOff = false;

    @OneToMany(mappedBy = "staffSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BreakTime> breakTimes = new ArrayList<>();
}
