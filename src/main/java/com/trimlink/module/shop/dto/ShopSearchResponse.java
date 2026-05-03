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
    private boolean active;
    private String ownerName;
    private String ownerPhone;
    private java.util.List<BankAccountDTO> bankAccounts;

    @Data
    @Builder
    public static class BankAccountDTO {
        private UUID id;
        private String bankName;
        private String accountNumber;
        private String accountHolder;
    }

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
                .active(shop.isActive())
                .ownerName(ownerName)
                .ownerPhone(ownerPhone)
                .bankAccounts(shop.getBankAccounts() != null ? shop.getBankAccounts().stream()
                        .filter(acc -> acc.isActive() && !acc.isDeleted())
                        .map(acc -> BankAccountDTO.builder()
                                .id(acc.getId())
                                .bankName(acc.getBankName())
                                .accountNumber(acc.getAccountNumber())
                                .accountHolder(acc.getAccountHolder())
                                .build())
                        .toList() : java.util.Collections.emptyList())
                .build();
    }
}
