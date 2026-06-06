package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class AdminDashboardInsightResponse {
    private String code;
    private String message;
    private BigDecimal value;
    private String unit;
    private Map<String, Object> metadata;
}
