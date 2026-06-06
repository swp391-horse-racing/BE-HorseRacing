package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AdminDashboardRevenueResponse {
    private Integer year;
    private Integer month;
    private String label;
    private BigDecimal amount;
    private BigDecimal growthPercent;
}
