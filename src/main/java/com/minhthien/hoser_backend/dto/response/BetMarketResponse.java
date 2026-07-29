package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.BetMarketStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BetMarketResponse {
    private Long id;
    private Long raceId;
    private String raceName;
    private Long tournamentId;
    private String tournamentName;
    private BetMarketStatus status;
    private BigDecimal minStake;
    private BigDecimal maxStake;
    private BigDecimal winningTaxPercent;
    private String note;
    private Long createdByAdminId;
    private String createdByAdminUsername;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private LocalDateTime settledAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<BetOptionResponse> options;
}
