package com.trimlink.module.support.entity;

import com.trimlink.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "support_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportMessage extends BaseEntity {

    @Column(nullable = false)
    private String senderUsername;

    @Column(length = 1000, nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean fromAdmin;

    @Builder.Default
    private boolean read = false;
}
