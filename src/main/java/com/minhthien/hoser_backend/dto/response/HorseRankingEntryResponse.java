package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class HorseRankingEntryResponse {
    private Integer rank;
    private Long horseId;
    private String horseName;
    private Long ownerId;
    private String ownerName;
    private Long winCount;
    private Long podiumCount;
    private Long raceCount;
    private BigDecimal totalPrizeAmount;
}
