package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RaceSimulationParticipantResponse {
    private Long participantId;
    private Long horseId;
    private String horseName;
    private Long jockeyId;
    private String jockeyName;
    private Integer gateNumber;
    private Long horseStarts;
    private Long horseWins;
    private Double horseWinRate;
    private Long jockeyStarts;
    private Long jockeyWins;
    private Double jockeyWinRate;
    private Double historyScore;
    private Integer rank;
    private Long finishTimeMillis;
    private List<RaceSimulationCheckpointResponse> checkpoints;
}
