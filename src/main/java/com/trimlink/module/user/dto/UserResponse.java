package com.trimlink.module.user.dto;

import com.trimlink.module.user.entity.ApprovalStatus;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String username;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String email;
    private String avatarUrl;
    private Role role;
    private boolean active;
    private ApprovalStatus approvalStatus;
    private boolean phoneVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BarberProfileResponse barberProfile;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BarberProfileResponse {
        private UUID id;
        private String bio;
        private Integer experienceYears;
        private Double averageRating;
        private Integer totalReviews;
        private boolean available;
        private ShopResponse shop;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopResponse {
        private UUID id;
        private String name;
        private String city;
        private String address;
        private String phone;
    }

    public static UserResponse from(User user) {
        if (user == null) return null;
        
        BarberProfileResponse profileRes = null;
        if (user.getBarberProfile() != null) {
            ShopResponse shopRes = null;
            if (user.getBarberProfile().getShop() != null) {
                shopRes = ShopResponse.builder()
                        .id(user.getBarberProfile().getShop().getId())
                        .name(user.getBarberProfile().getShop().getName())
                        .city(user.getBarberProfile().getShop().getCity())
                        .address(user.getBarberProfile().getShop().getAddress())
                        .phone(user.getBarberProfile().getShop().getPhone())
                        .build();
            }

            profileRes = BarberProfileResponse.builder()
                    .id(user.getBarberProfile().getId())
                    .bio(user.getBarberProfile().getBio())
                    .experienceYears(user.getBarberProfile().getExperienceYears())
                    .averageRating(user.getBarberProfile().getAverageRating() != null ? user.getBarberProfile().getAverageRating().doubleValue() : 0.0)
                    .totalReviews(user.getBarberProfile().getTotalReviews())
                    .available(user.getBarberProfile().isAvailable())
                    .shop(shopRes)
                    .build();
        }

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .active(user.isActive())
                .approvalStatus(user.getApprovalStatus())
                .phoneVerified(user.isPhoneVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .barberProfile(profileRes)
                .build();
    }
}
