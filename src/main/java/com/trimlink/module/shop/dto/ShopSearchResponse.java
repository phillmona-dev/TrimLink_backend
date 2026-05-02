package com.trimlink.module.shop.dto;

import com.trimlink.module.shop.entity.BarberShop;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ShopSearchResponse {
    private UUID id;
    private String name;
    private String phone;
    private String address;
    private String city;
    private String description;
    private String logoUrl;
    private Double latitude;
    private Double longitude;
    private String ownerName;
    private String ownerPhone;

    public static ShopSearchResponse from(BarberShop shop, String ownerName, String ownerPhone) {
        return ShopSearchResponse.builder()
                .id(shop.getId())
                .name(shop.getName())
                .phone(shop.getPhone())
                .address(shop.getAddress())
                .city(shop.getCity())
                .description(shop.getDescription())
                .logoUrl(shop.getLogoUrl())
                .latitude(shop.getLatitude())
                .longitude(shop.getLongitude())
                .ownerName(ownerName)
                .ownerPhone(ownerPhone)
                .build();
    }
}
