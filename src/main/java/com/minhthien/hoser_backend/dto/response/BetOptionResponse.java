package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BetOptionResponse {
    private Long participantId;
    private Long horseId;
    private String horseName;
    private Long jockeyId;
    private String jockeyUsername;
    private Integer gateNumber;
    private RaceParticipantStatus status;
}
