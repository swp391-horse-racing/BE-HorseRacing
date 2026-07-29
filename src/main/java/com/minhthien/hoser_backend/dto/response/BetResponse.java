package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.BetStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BetResponse {
    private Long id;
    private Long marketId;
    private Long raceId;
    private String raceName;
    private Long participantId;
    private Long horseId;
    private String horseName;
    private Long userId;
    private String username;
    private BigDecimal stakeAmount;
    private BigDecimal potentialPayoutAmount;
    private BigDecimal winningTaxPercent;
    private BigDecimal winningTaxAmount;
    private BigDecimal estimatedWinningTaxAmount;
    private BigDecimal estimatedNetPayoutAmount;
    private BigDecimal grossProfitAmount;
    private BigDecimal netProfitAmount;
    private BetStatus status;
    private LocalDateTime placedAt;
    private LocalDateTime lockedAt;
    private LocalDateTime settledAt;
}
