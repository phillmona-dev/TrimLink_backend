package com.trimlink.module.shop.entity;

import com.trimlink.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_work_logs", indexes = {
        @Index(name = "idx_work_logs_staff_date", columnList = "staff_id, log_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyWorkLog extends BaseEntity {

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "customer_count", nullable = false)
    private int customerCount;

    @Column(name = "notes", length = 500)
    private String notes;
}
