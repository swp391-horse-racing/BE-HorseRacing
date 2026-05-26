package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RacePayoutStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TournamentPayoutResponse {
    private Long raceResultId;
    private Long tournamentId;
    private String tournamentName;
    private Long raceId;
    private String raceName;
    private Long participantId;
    private Integer rank;
    private Long horseId;
    private String horseName;
    private Long ownerId;
    private String ownerUsername;
    private Long jockeyId;
    private String jockeyUsername;
    private BigDecimal prizeAmount;
    private BigDecimal ownerPrizeAmount;
    private BigDecimal jockeyPrizeAmount;
    private BigDecimal jockeyPrizePercent;
    private BigDecimal unpaidOwnerAmount;
    private BigDecimal unpaidJockeyAmount;
    private RacePayoutStatus payoutStatus;
    private LocalDateTime finalizedAt;
}
