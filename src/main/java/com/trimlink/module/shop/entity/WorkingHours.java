package com.trimlink.module.shop.entity;

import com.trimlink.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Operating hours for a staffshop on a specific day of the week.
 * Closed flag allows marking holidays without deleting the record.
 */
@Entity
@Table(name = "working_hours", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"shop_id", "day_of_week"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkingHours extends BaseEntity {

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private StaffShop shop;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 15)
    private DayOfWeek dayOfWeek;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "closed", nullable = false)
    @Builder.Default
    private boolean closed = false;
}
