package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RacePayoutStatus;
import com.minhthien.hoser_backend.enums.RaceResultSource;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RaceResultResponse {
    private Long id;
    private Long raceId;
    private Long participantId;
    private Long ownerId;
    private String ownerUsername;
    private Long horseId;
    private String horseName;
    private Long jockeyId;
    private String jockeyUsername;
    private Integer rank;
    private Long finishTimeMillis;
    private RaceResultSource source;
    private String simulationRunId;
    private Long baseFinishTimeMillis;
    private Long penaltyTimeMillis;
    private RaceParticipantStatus status;
    private Integer jockeyChallengePoints;
    private BigDecimal prizeAmount;
    private BigDecimal ownerPrizeAmount;
    private BigDecimal jockeyPrizeAmount;
    private BigDecimal jockeyPrizePercent;
    private RacePayoutStatus payoutStatus;
    private String note;
    private Long finalizedBy;
    private LocalDateTime finalizedAt;
}
