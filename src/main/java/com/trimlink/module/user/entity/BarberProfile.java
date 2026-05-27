package com.trimlink.module.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.trimlink.common.audit.BaseEntity;
import com.trimlink.module.shop.entity.BarberShop;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended profile for barbers. Linked 1:1 to User.
 * Tracks average rating, bio, experience, and their assigned shop.
 */
@Entity
@Table(name = "barber_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.hibernate.envers.Audited
public class BarberProfile extends BaseEntity {

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private BarberShop shop;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "average_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_reviews")
    @Builder.Default
    private int totalReviews = 0;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private boolean available = true;

    // Services this barber offers — join table
    @org.hibernate.envers.NotAudited
    @OneToMany(mappedBy = "barberProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BarberServiceAssignment> serviceAssignments = new ArrayList<>();

    /**
     * Recalculate average rating after a new review is submitted.
     */
    public void addReview(BigDecimal newRating) {
        BigDecimal total = this.averageRating.multiply(BigDecimal.valueOf(this.totalReviews))
                .add(newRating);
        this.totalReviews++;
        this.averageRating = total.divide(BigDecimal.valueOf(this.totalReviews), 2,
                java.math.RoundingMode.HALF_UP);
    }

    public void updateRatingSummary(BigDecimal averageRating, long totalReviews) {
        this.averageRating = averageRating != null ? averageRating : BigDecimal.ZERO;
        this.totalReviews = Math.toIntExact(totalReviews);
    }
}
