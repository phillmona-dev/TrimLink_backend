package com.trimlink.module.user.entity;

import com.trimlink.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Barber's own working schedule for a given day.
 * Takes precedence over shop working hours for slot generation.
 * If no barber schedule exists for a day, falls back to shop hours.
 */
@Entity
@Table(name = "barber_schedules", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"barber_profile_id", "day_of_week"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BarberSchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barber_profile_id", nullable = false)
    private BarberProfile barberProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 15)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** If true, barber is off this day regardless of shop hours. */
    @Column(name = "day_off", nullable = false)
    @Builder.Default
    private boolean dayOff = false;

    @OneToMany(mappedBy = "barberSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BreakTime> breakTimes = new ArrayList<>();
}
