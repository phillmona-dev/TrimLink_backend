package com.trimlink.module.admin.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopFinanceSummary {
    private UUID shopId;
    private String shopName;
    private String ownerName;
    private BigDecimal totalRevenue;
    private BigDecimal adminShare;
    private long totalTransactions;
    private LocalDateTime lastTransactionAt;
}
