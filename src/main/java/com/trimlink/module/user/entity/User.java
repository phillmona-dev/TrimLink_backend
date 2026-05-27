package com.trimlink.module.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.trimlink.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Core platform user.
 * Authentication is phone-based (OTP); email is optional.
 * The 'role' field drives all authorization decisions.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_username", columnList = "username", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.hibernate.envers.Audited
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @JsonIgnore
    @org.hibernate.envers.NotAudited
    @Column(name = "password")
    private String password;

    @Column(name = "provider", length = 20)
    private String provider;

    @JsonIgnore
    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "phone_number", unique = true, length = 20)
    private String phoneNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.APPROVED;

    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private boolean phoneVerified = false;

    // Bidirectional 1:1 with BarberProfile (null unless role = BARBER or OWNER)
    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private BarberProfile barberProfile;
}
