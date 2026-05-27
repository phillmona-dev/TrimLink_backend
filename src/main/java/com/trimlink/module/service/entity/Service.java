package com.trimlink.module.service.entity;

import com.trimlink.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A service offered in the platform (e.g., Haircut, Shave, Edge-up).
 * Duration (minutes) drives slot generation and queue ETA calculation.
 */
@Entity
@Table(name = "services", indexes = {
        @Index(name = "idx_services_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.hibernate.envers.Audited
public class Service extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 400)
    private String description;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    /**
     * Duration in minutes. Used for time-slot generation & queue ETA.
     */
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Optional link to a specific shop. If null, this is a platform-wide global service.
     */
    @Column(name = "shop_id")
    private java.util.UUID shopId;
}
