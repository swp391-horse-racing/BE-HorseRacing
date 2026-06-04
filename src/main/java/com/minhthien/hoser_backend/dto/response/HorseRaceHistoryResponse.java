package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HorseRaceHistoryResponse {
    private Long tournamentId;
    private String tournamentName;
    private Long raceId;
    private String raceName;
    private LocalDateTime scheduledStartAt;
    private Integer rank;
    private RaceParticipantStatus status;
    private Long finishTimeMillis;
    private LocalDateTime finalizedAt;
}
