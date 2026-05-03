package com.trimlink.module.user.entity;

import com.trimlink.common.audit.BaseEntity;
import com.trimlink.module.service.entity.Service;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Join entity: a staff's price override for a given service.
 * A staff can charge differently from the base service price.
 */
@Entity
@Table(name = "staff_service_assignments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"staff_profile_id", "service_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffServiceAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_profile_id", nullable = false)
    private StaffProfile staffProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    /**
     * Staff-specific price override. Null = use service base price.
     */
    @Column(name = "custom_price", precision = 10, scale = 2)
    private BigDecimal customPrice;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
