package com.trimlink.module.shop.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.trimlink.common.audit.BaseEntity;
import com.trimlink.module.user.entity.BarberProfile;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a physical barbershop location.
 * A shop has many barbers and an operating schedule.
 */
@Entity
@Table(name = "barber_shops", indexes = {
        @Index(name = "idx_shops_city", columnList = "city")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BarberShop extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "latitude", precision = 10)
    private Double latitude;

    @Column(name = "longitude", precision = 10)
    private Double longitude;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform")
    private ShopPlatform platform;

    @JsonIgnore
    @OneToMany(mappedBy = "shop", fetch = FetchType.LAZY)
    @Builder.Default
    private List<BarberProfile> barbers = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkingHours> workingHours = new ArrayList<>();

    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShopBankAccount> bankAccounts = new ArrayList<>();
}
