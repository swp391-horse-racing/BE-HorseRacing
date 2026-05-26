package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.TournamentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class TournamentStatisticsResponse {
    private Long tournamentId;
    private String tournamentName;
    private TournamentStatus tournamentStatus;
    private LocalDateTime finalizedAt;
    private Long finalizedBy;
    private Integer ownerCount;
    private Integer horseCount;
    private Integer jockeyCount;
    private Integer refereeCount;
    private Integer raceResultCount;
    private Integer pendingComplaintCountAtFinalize;
    private Map<String, Long> registrationsByStatus;
    private Map<String, Long> racesByStatus;
    private Map<String, Long> participantsByStatus;
    private Map<String, Long> complaintsByStatus;
    private Map<String, BigDecimal> prizePayoutTotalsByStatus;
    private BigDecimal totalPrizeAmount;
    private BigDecimal paidPrizeAmount;
    private BigDecimal unpaidPrizeAmount;
    private BigDecimal notEligiblePrizeAmount;
}
