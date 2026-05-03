package com.trimlink.module.booking.entity;

import com.trimlink.common.audit.BaseEntity;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.entity.StaffProfile;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Customer review for a completed appointment.
 * One review per appointment (enforced by unique constraint).
 */
@Entity
@Table(name = "reviews", uniqueConstraints = {
        @UniqueConstraint(columnNames = "appointment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_profile_id", nullable = false)
    private StaffProfile staffProfile;

    @Column(name = "rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;   // 1.0 – 5.0

    @Column(name = "comment", length = 500)
    private String comment;
}
