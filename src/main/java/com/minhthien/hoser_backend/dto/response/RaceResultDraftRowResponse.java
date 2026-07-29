package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RaceResultDraftRowResponse {
    private Long participantId;
    private Long horseId;
    private String horseName;
    private Long jockeyId;
    private String jockeyName;
    private Integer gateNumber;
    private Integer baseRank;
    private Integer rank;
    private Long baseFinishTimeMillis;
    private Long penaltyTimeMillis;
    private Long finishTimeMillis;
    private RaceParticipantStatus status;
    private String disqualificationReason;
}
