package com.trimlink.module.user.dto;

import com.trimlink.module.user.entity.BarberProfile;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BarberResponse {
    private UUID id;
    private UserResponse user;
    private String bio;
    private Integer experienceYears;
    private BigDecimal averageRating;
    private int totalReviews;
    private boolean available;
    private List<BarberServiceAssignmentResponse> serviceAssignments;

    public static BarberResponse from(BarberProfile profile) {
        if (profile == null) return null;
        
        List<BarberServiceAssignmentResponse> assignments = profile.getServiceAssignments() != null 
            ? profile.getServiceAssignments().stream()
                .filter(a -> a.getService().isActive())
                .map(a -> BarberServiceAssignmentResponse.builder()
                    .assignmentId(a.getId())
                    .barberId(profile.getId())
                    .serviceId(a.getService().getId())
                    .serviceName(a.getService().getName())
                    .serviceDescription(a.getService().getDescription())
                    .durationMinutes(a.getService().getDurationMinutes())
                    .basePrice(a.getService().getBasePrice())
                    .customPrice(a.getCustomPrice())
                    .effectivePrice(a.getCustomPrice() != null ? a.getCustomPrice() : a.getService().getBasePrice())
                    .active(true)
                    .build())
                .toList()
            : List.of();

        return BarberResponse.builder()
                .id(profile.getId())
                .user(UserResponse.from(profile.getUser()))
                .bio(profile.getBio())
                .experienceYears(profile.getExperienceYears())
                .averageRating(profile.getAverageRating())
                .totalReviews(profile.getTotalReviews())
                .available(profile.isAvailable())
                .serviceAssignments(assignments)
                .build();
    }
}
