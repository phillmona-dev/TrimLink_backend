package com.trimlink.module.service.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a standard catalog haircut style inside the platform library.
 */
@Entity
@Table(name = "haircut_styles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HaircutStyle {

    @Id
    @Column(name = "id", nullable = false, length = 255)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;
}
