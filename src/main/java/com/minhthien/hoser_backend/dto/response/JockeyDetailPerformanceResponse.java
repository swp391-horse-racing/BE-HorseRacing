package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class JockeyDetailPerformanceResponse {
    private Integer totalRaces;
    private Integer wins;
    private BigDecimal winRate;
    private Map<String, Long> rankCounts;
}
