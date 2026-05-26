package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RacePayoutStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TournamentLeaderboardEntryResponse {
    private Long id;
    private Long tournamentId;
    private Long raceId;
    private String raceName;
    private LocalDateTime raceScheduledStartAt;
    private LocalDateTime raceScheduledEndAt;
    private Long raceResultId;
    private Long participantId;
    private Integer raceRank;
    private Long finishTimeMillis;
    private RaceParticipantStatus resultStatus;
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
    private RacePayoutStatus payoutStatus;
    private Long resultFinalizedBy;
    private LocalDateTime resultFinalizedAt;
    private Long tournamentFinalizedBy;
    private LocalDateTime tournamentFinalizedAt;
}
