package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class JockeyRankingEntryResponse {
    private Integer rank;
    private Long jockeyId;
    private String jockeyUsername;
    private String jockeyFullName;
    private Long winCount;
    private Long podiumCount;
    private Long raceCount;
    private BigDecimal totalPrizeAmount;
}
