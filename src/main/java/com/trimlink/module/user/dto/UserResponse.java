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
    private StaffProfileResponse staffProfile;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffProfileResponse {
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
        
        StaffProfileResponse profileRes = null;
        if (user.getStaffProfile() != null) {
            ShopResponse shopRes = null;
            if (user.getStaffProfile().getShop() != null) {
                shopRes = ShopResponse.builder()
                        .id(user.getStaffProfile().getShop().getId())
                        .name(user.getStaffProfile().getShop().getName())
                        .city(user.getStaffProfile().getShop().getCity())
                        .address(user.getStaffProfile().getShop().getAddress())
                        .phone(user.getStaffProfile().getShop().getPhone())
                        .build();
            }

            profileRes = StaffProfileResponse.builder()
                    .id(user.getStaffProfile().getId())
                    .bio(user.getStaffProfile().getBio())
                    .experienceYears(user.getStaffProfile().getExperienceYears())
                    .averageRating(user.getStaffProfile().getAverageRating() != null ? user.getStaffProfile().getAverageRating().doubleValue() : 0.0)
                    .totalReviews(user.getStaffProfile().getTotalReviews())
                    .available(user.getStaffProfile().isAvailable())
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
                .staffProfile(profileRes)
                .build();
    }
}
