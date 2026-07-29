package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceSimulationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RaceSimulationResponse {
    private Long raceId;
    private String runId;
    private RaceSimulationStatus status;
    private String algorithmVersion;
    private String seed;
    private Long playbackDurationMs;
    private LocalDateTime generatedAt;
    private LocalDateTime playbackEndsAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime serverTime;
    private List<RaceSimulationParticipantResponse> participants;
}
