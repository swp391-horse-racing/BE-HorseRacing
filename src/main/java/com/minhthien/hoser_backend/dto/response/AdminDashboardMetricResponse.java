package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AdminDashboardMetricResponse {
    private BigDecimal value;
    private BigDecimal growthPercent;
}
