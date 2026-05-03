package com.trimlink.module.shop.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.trimlink.common.audit.BaseEntity;
import com.trimlink.module.user.entity.StaffProfile;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a physical staffshop location.
 * A shop has many staffs and an operating schedule.
 */
@Entity
@Table(name = "staff_shops", indexes = {
        @Index(name = "idx_shops_city", columnList = "city")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffShop extends BaseEntity {

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

    @JsonIgnore
    @OneToMany(mappedBy = "shop", fetch = FetchType.LAZY)
    @Builder.Default
    private List<StaffProfile> staffs = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkingHours> workingHours = new ArrayList<>();

    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShopBankAccount> bankAccounts = new ArrayList<>();
}
